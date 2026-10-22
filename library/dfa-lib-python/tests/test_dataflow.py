import pytest

from dfa_lib_python.dataflow import Dataflow
from dfa_lib_python.transformation import Transformation
from dfa_lib_python.set import Set
from dfa_lib_python.set_type import SetType
from dfa_lib_python.attribute import Attribute
from dfa_lib_python.attribute_type import AttributeType


def test_get_transformations_pass():
    attributes = [Attribute("att1", AttributeType.TEXT)]
    sets = [Set("set1", SetType.INPUT, attributes)]
    transformations = [Transformation("tf1", sets=sets)]
    expected_result = [x.get_specification() for x in transformations]
    dataflow = Dataflow("df", transformations=transformations)
    assert dataflow.transformations == expected_result


def test_set_transformations_pass():
    attributes = [Attribute("att1", AttributeType.TEXT)]
    sets = [Set("set1", SetType.INPUT, attributes)]
    transformations = [Transformation("tf1", sets=sets)]
    new_transformations = [Transformation("tf2", sets=sets)]
    expected_result = [x.get_specification() for x in new_transformations]
    dataflow = Dataflow("df", transformations=transformations)
    dataflow.transformations = new_transformations
    assert dataflow.transformations == expected_result


def test_get_specification_pass():
    tag = "df"
    sets = [Set("set1", SetType.INPUT,
            [Attribute("att1", AttributeType.TEXT)])]
    transformations = [Transformation(tag, sets)]
    expected_result = {"transformations": [x.get_specification()
                       for x in transformations], "tag": tag}
    dataflow = Dataflow("df", transformations=transformations)
    assert dataflow.get_specification() == expected_result


def test_sem_origem_o_json_nao_carrega_o_campo():
    spec = Dataflow("teste").get_specification()
    assert "origin" not in spec and "generator" not in spec


def test_origem_e_gerador_declarados_vao_no_json():
    spec = Dataflow("teste", origin="curated", generator="ferramenta 1.0").get_specification()
    assert spec["origin"] == "curated"
    assert spec["generator"] == "ferramenta 1.0"


def test_origem_fora_do_vocabulario_e_recusada():
    with pytest.raises(AssertionError):
        Dataflow("teste", origin="llm")
