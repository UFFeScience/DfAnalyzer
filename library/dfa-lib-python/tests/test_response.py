import warnings

import pytest

from dfa_lib_python import dataflow as dataflow_module
from dfa_lib_python.dataflow import Dataflow
from dfa_lib_python.response import ProvenanceRejectedWarning, check_response


class _Resposta:
    def __init__(self, status_code, text=""):
        self.status_code = status_code
        self.text = text


def test_resposta_de_sucesso_nao_avisa(capsys, monkeypatch):
    monkeypatch.setattr(dataflow_module.requests, "post", lambda url, json=None: _Resposta(200))
    with warnings.catch_warnings():
        warnings.simplefilter("error")
        Dataflow("teste").save()
    assert capsys.readouterr().out == "200\n"


def test_recusa_do_backend_vira_aviso_com_o_motivo(monkeypatch):
    motivo = '{"error": "DATASET_TABLE_CONFLICT", "message": "a tabela ds_x ja existe"}'
    monkeypatch.setattr(dataflow_module.requests, "post", lambda url, json=None: _Resposta(409, motivo))
    with pytest.warns(ProvenanceRejectedWarning, match="409.*ds_x ja existe"):
        Dataflow("teste").save()


def test_recusa_nao_interrompe_o_programa():
    with warnings.catch_warnings():
        warnings.simplefilter("ignore")
        check_response(_Resposta(500, "erro interno"), "task 1 de 'x'")
