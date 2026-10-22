import warnings
import pytest

from dfa_lib_python.dataset import DataSet
from dfa_lib_python.element import Element
from dfa_lib_python.dataset import MAX_VALUE_LENGTH, DataSet, ProvenanceValueWarning


def test_get_elements_pass():
    tag = "dstag"
    elements = [Element([1, 2, 3, 4])]
    expected_result = [elements[0].values]
    dataset = DataSet(tag, elements)
    assert dataset.elements == expected_result


def test_set_elements_pass():
    tag = "dstag"
    elements = [Element([1, 2, 3, 4])]
    new_elements = [Element([5, 6, 7, 8])]
    expected_result = [new_elements[0].values]
    dataset = DataSet(tag, elements)
    dataset.elements = new_elements
    assert dataset.elements == expected_result


def test_valores_que_cabem_nao_avisam():
    with warnings.catch_warnings():
        warnings.simplefilter("error")
        DataSet("ok", [Element(["a" * MAX_VALUE_LENGTH, 42, 3.5])])


def test_valor_maior_que_a_coluna_avisa_com_dataset_e_posicao():
    with pytest.warns(ProvenanceValueWarning) as record:
        DataSet("vendas_brutas", [Element(["curto", "x" * (MAX_VALUE_LENGTH + 1)])])

    message = str(record[0].message)
    assert "vendas_brutas" in message
    assert "posicao 1" in message


def test_aviso_nao_impede_o_dataset_de_ser_montado():
    with pytest.warns(ProvenanceValueWarning):
        ds = DataSet("d", [Element(["x" * 1000])])

    assert ds.elements == [["x" * 1000]]
