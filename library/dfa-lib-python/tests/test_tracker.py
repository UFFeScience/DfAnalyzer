"""TaskTracker: numeracao e dependencia de tasks em tempo de execucao."""
import multiprocessing
from concurrent.futures import ThreadPoolExecutor

import pytest

from dfa_lib_python import execution
from dfa_lib_python.dataflow import Dataflow
from dfa_lib_python.tracker import TaskTracker, _dependency_records


@pytest.fixture
def tracker(tmp_path, monkeypatch):
    saves = []
    monkeypatch.setattr(Dataflow, "save", lambda self: saves.append(self._tag))
    monkeypatch.setattr(execution, "_execution_tag", "exec-" + tmp_path.name)
    t = TaskTracker(Dataflow("Teste"), registry_dir=str(tmp_path))
    t.saves = saves
    return t


def deps(task):
    """{tag produtora: [identificadores]} como o servidor vai ler."""
    spec = task.get_specification().get("dependency")
    if not spec:
        return {}
    tags = [t["tag"] for t in spec["tags"]]
    out = {tag: [] for tag in tags}
    for record in spec["ids"]:
        for tag, identifier in zip(tags, record["id"].split(",")):
            if int(identifier) not in out[tag]:
                out[tag].append(int(identifier))
    return out


def ident(task):
    return int(task.get_specification()["id"])


def test_definicao_e_salva_uma_vez_e_so_na_primeira_task(tracker):
    assert tracker.saves == []  # Criar o tracker nao fala com o servidor.
    tracker.task("A")
    tracker.task("A")
    tracker.task("B", depends_on=["A"])
    assert tracker.saves == ["teste"]


def test_cadeia_linear(tracker):
    a = tracker.task("A")
    b = tracker.task("B", depends_on=["A"])
    assert (ident(a), ident(b)) == (1, 1)
    assert deps(a) == {}
    assert deps(b) == {"a": [1]}


def test_fan_in_de_duas_transformacoes(tracker):
    tracker.task("A")
    tracker.task("B")
    c = tracker.task("C", depends_on=["A", "B"])
    assert deps(c) == {"a": [1], "b": [1]}
    assert c.get_specification()["dependency"]["ids"] == [{"id": "1,1"}]


def test_laco_cada_volta_e_uma_task_ligada_a_produtora_da_mesma_volta(tracker):
    pares = []
    for _ in range(3):
        a = tracker.task("A")
        b = tracker.task("B", depends_on=["A"])
        pares.append((ident(a), ident(b), deps(b)))
    assert pares == [(1, 1, {"a": [1]}), (2, 2, {"a": [2]}), (3, 3, {"a": [3]})]


def test_consumidor_depois_do_laco_depende_de_todas_as_voltas(tracker):
    for _ in range(3):
        tracker.task("A")
    b = tracker.task("B", depends_on=["A"])
    assert deps(b) == {"a": [1, 2, 3]}


def test_produtor_fora_do_laco_do_consumidor(tracker):
    tracker.task("A")
    vistos = [deps(tracker.task("B", depends_on=["A"])) for _ in range(3)]
    assert vistos == [{"a": [1]}] * 3


def test_laco_externo_com_laco_interno_produtor(tracker):
    vistos = []
    for _ in range(2):
        for _ in range(2):
            tracker.task("A")
        vistos.append(deps(tracker.task("B", depends_on=["A"])))
    assert vistos == [{"a": [1, 2]}, {"a": [3, 4]}]


def test_fan_in_com_contagens_diferentes_completa_a_coluna_curta():
    assert _dependency_records([[1, 2, 3], [1]]) == ["1,1", "2,1", "3,1"]


def test_dependencia_sem_produtora_avisa_e_nao_inventa(tracker):
    with pytest.warns(UserWarning, match="nenhuma task de 'a'"):
        b = tracker.task("B", depends_on=["A"])
    assert deps(b) == {}


def test_limite_conhecido_dois_lacos_separados(tracker):
    """Limite conhecido: a regra segue a ordem de criação, não o fluxo de dados."""
    for _ in range(3):
        tracker.task("A")
    vistos = [deps(tracker.task("B", depends_on=["A"])) for _ in range(3)]
    assert vistos == [{"a": [1, 2, 3]}, {"a": [3]}, {"a": [3]}]


def test_threads_cada_trabalhador_liga_a_sua_propria_produtora(tracker):
    def trabalho(_):
        a = tracker.task("A")
        b = tracker.task("B", depends_on=["A"])
        return ident(a), ident(b), deps(b)

    with ThreadPoolExecutor(max_workers=4) as pool:
        resultados = list(pool.map(trabalho, range(40)))

    assert sorted(a for a, _, _ in resultados) == list(range(1, 41))
    assert sorted(b for _, b, _ in resultados) == list(range(1, 41))
    assert all(d == {"a": [a]} for a, _, d in resultados)


def test_consumidor_na_thread_principal_depois_do_pool(tracker):
    with ThreadPoolExecutor(max_workers=3) as pool:
        list(pool.map(lambda _: tracker.task("A"), range(5)))
    b = tracker.task("B", depends_on=["A"])
    assert deps(b) == {"a": [1, 2, 3, 4, 5]}


def _trabalho_em_processo(args):
    registry_dir, exec_tag = args
    execution._execution_tag = exec_tag
    Dataflow.save = lambda self: None
    t = TaskTracker(Dataflow("Teste"), registry_dir=registry_dir)
    a = t.task("A")
    b = t.task("B", depends_on=["A"])
    return ident(a), ident(b), deps(b)


@pytest.mark.parametrize("metodo", ["fork", "spawn"])
def test_processos_numeram_sem_colidir_e_o_pai_ve_as_tasks_dos_filhos(tracker, tmp_path, metodo):
    tracker.task("Inicio")  # O pai abre o registro (e salva a definicao)
    ctx = multiprocessing.get_context(metodo)
    args = (str(tmp_path), execution.get_execution_tag())
    with ctx.Pool(3) as pool:
        resultados = pool.map(_trabalho_em_processo, [args] * 9)

    assert sorted(a for a, _, _ in resultados) == list(range(1, 10))
    assert all(d == {"a": [a]} for a, _, d in resultados)
    fim = tracker.task("Fim", depends_on=["B"])
    assert deps(fim) == {"b": list(range(1, 10))}
    assert tracker.saves == ["teste"]
