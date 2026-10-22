"""Numeracao e dependencia de tasks em tempo de execucao."""

import atexit
import json
import os
import re
import tempfile
import threading
import warnings
from contextlib import contextmanager

from .dataflow import Dataflow
from .dependency import Dependency
from .execution import get_execution_tag
from .task import Task

try:
    import fcntl
except ImportError:  # pragma: no cover - Windows: sem trava entre processos
    fcntl = None


class TaskTracker(object):
    """Numera as tasks de um dataflow e resolve suas dependencias."""

    def __init__(self, dataflow, registry_dir=None):
        assert isinstance(dataflow, Dataflow), "The dataflow must be valid."
        self._dataflow = dataflow
        self._dataflow_tag = dataflow._tag
        self._registry_dir = registry_dir or tempfile.gettempdir()
        self._thread_lock = threading.Lock()
        self._owner_pid = os.getpid()
        self._owned_paths = set()
        # Processos criados por `spawn` reimportam a biblioteca e abririam outra execução.
        os.environ.setdefault("DFA_EXECUTION_TAG", get_execution_tag())
        atexit.register(self._cleanup)

    # ------------------------------------------------------------ registro

    def _registry_path(self):
        safe = lambda text: re.sub(r"[^A-Za-z0-9_.-]", "_", text)
        return os.path.join(
            self._registry_dir,
            "dfa-tracker-{0}-{1}.jsonl".format(safe(self._dataflow_tag), safe(get_execution_tag())),
        )

    @contextmanager
    def _registry(self):
        """Abre o registro com trava exclusiva; entrega (entradas, arquivo)."""
        path = self._registry_path()
        with self._thread_lock:
            created = not os.path.exists(path)
            handle = open(path, "a+")
            try:
                if fcntl is not None:
                    fcntl.flock(handle, fcntl.LOCK_EX)
                handle.seek(0)
                lines = [line for line in handle.read().splitlines() if line.strip()]
                # Outro processo pode ter criado o arquivo entre o exists() e a trava.
                first = created and not lines
                if first:
                    self._owned_paths.add(path)
                    handle.write(json.dumps({"dataflow": self._dataflow_tag}) + "\n")
                    handle.flush()
                    # Com a trava, nenhuma task é enviada antes da definição.
                    self._dataflow.save()
                entries = [json.loads(line) for line in lines]
                yield [e for e in entries if "tag" in e], handle
                handle.flush()
            finally:
                if fcntl is not None:
                    fcntl.flock(handle, fcntl.LOCK_UN)
                handle.close()

    def _cleanup(self):
        if os.getpid() != self._owner_pid:
            return
        for path in self._owned_paths:
            try:
                os.remove(path)
            except OSError:
                pass

    # --------------------------------------------------------------- valor

    def value(self, compute):
        """Avalia a expressao de um atributo sem deixar a captura de proveniencia derrubar o programa."""
        try:
            return compute()
        except Exception as exc:  # noqa: BLE001 - qualquer falha da expressao
            warnings.warn(
                "valor de proveniencia nao capturado ({0}: {1}) - gravado vazio; o "
                "programa continua.".format(type(exc).__name__, exc),
                stacklevel=2,
            )
            return ""

    # ---------------------------------------------------------------- task

    def task(self, transformation_tag, depends_on=(), **task_kwargs):
        """Cria a proxima ``Task`` de uma transformacao."""
        tag = transformation_tag.lower()
        context = "{0}:{1}".format(os.getpid(), threading.get_ident())

        with self._registry() as (entries, handle):
            identifier = 1 + sum(1 for e in entries if e["tag"] == tag)
            last_mine = max(
                [e["seq"] for e in entries if e["tag"] == tag and e["ctx"] == context] or [0])

            tags, ids = [], []
            for producer in depends_on:
                producer = producer.lower()
                chosen = _select_producers(entries, producer, last_mine, context)
                if chosen:
                    tags.append(producer)
                    ids.append([e["id"] for e in chosen])
                else:
                    warnings.warn(
                        "transformacao '{0}': nenhuma task de '{1}' foi criada antes desta - "
                        "a dependencia nao sera registrada.".format(tag, producer),
                        stacklevel=2,
                    )

            handle.write(json.dumps(
                {"seq": len(entries) + 1, "tag": tag, "id": identifier, "ctx": context}) + "\n")

        task = Task(identifier, self._dataflow_tag, transformation_tag, **task_kwargs)
        if tags:
            task.add_dependency(Dependency(tags, _dependency_records(ids)))
        return task


def _select_producers(entries, producer, last_mine, context):
    """As tasks de `producer` de que a nova task depende."""
    of_producer = [e for e in entries if e["tag"] == producer]
    newer = [e for e in of_producer if e["seq"] > last_mine]
    return [e for e in newer if e["ctx"] == context] or newer or of_producer[-1:]


def _dependency_records(ids):
    """Cada registro é uma string "a,b,..." com um identificador por tag, na ordem das tags."""
    width = max(len(column) for column in ids)
    return [
        ",".join(str(column[min(row, len(column) - 1)]) for column in ids)
        for row in range(width)
    ]
