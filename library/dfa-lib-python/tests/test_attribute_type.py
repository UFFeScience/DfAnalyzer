from dfa_lib_python.attribute_type import AttributeType


def test_tipos_sao_os_que_o_backend_java_aceita():
    # Tipo desconhecido do backend é descartado na ingestão com HTTP 200.
    assert {t.value for t in AttributeType} == {"TEXT", "NUMERIC", "FILE"}


def test_rdfile_nao_existe_mais():
    assert not hasattr(AttributeType, "RDFILE")
