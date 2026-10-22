"""Identidade de execucao."""

import os
import uuid
from datetime import datetime


def _generate_execution_tag():
    return "{0}-{1}".format(
        datetime.now().strftime("%Y%m%d-%H%M%S"), uuid.uuid4().hex[:6])


_execution_tag = os.environ.get("DFA_EXECUTION_TAG") or _generate_execution_tag()


def get_execution_tag():
    """Tag da execucao corrente deste processo."""
    return _execution_tag


def set_execution_tag(tag):
    """Fixa a tag da execucao."""
    global _execution_tag
    assert tag, "A tag da execucao nao pode ser vazia."
    _execution_tag = str(tag)
    return _execution_tag


def new_execution_tag():
    """Abre uma execucao nova dentro do mesmo processo."""
    return set_execution_tag(_generate_execution_tag())
