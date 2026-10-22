from dfa_lib_python.dataflow import Dataflow
from dfa_lib_python.transformation import Transformation
from dfa_lib_python.attribute import Attribute
from dfa_lib_python.attribute_type import AttributeType
from dfa_lib_python.set import Set
from dfa_lib_python.set_type import SetType
from dfa_lib_python.task import Task
from dfa_lib_python.dataset import DataSet
from dfa_lib_python.element import Element
from dfa_lib_python.program import Program


import time
from datetime import datetime

dataflow_tag = "soma"
df = Dataflow(dataflow_tag)

# Proveniência prospectiva
tf1 = Transformation("ExtrairNumeros")
tf1_input = Set("iExtrairNumeros", SetType.INPUT, 
    [Attribute("SOMA_FILE", AttributeType.FILE)])
tf1_output = Set("oExtrairNumeros", SetType.OUTPUT, 
    [Attribute("PRIMEIRO_NUMERO", AttributeType.NUMERIC), 
    Attribute("SEGUNDO_NUMERO", AttributeType.NUMERIC)])
tf1.set_sets([tf1_input, tf1_output])
df.add_transformation(tf1)

tf2 = Transformation("ExecutarSoma")
tf1_output.set_type(SetType.INPUT)
tf1_output.dependency=tf1._tag
tf2_output = Set("oExecutarSoma", SetType.OUTPUT, 
    [Attribute("RESULTADO_SOMA", AttributeType.NUMERIC)])
tf2.set_sets([tf1_output, tf2_output])
df.add_transformation(tf2)

df.save()

#Proveniência retrospectiva
t1 = Task(1, dataflow_tag, "ExtrairNumeros")
t1_input = DataSet("iExtrairNumeros", [Element(["/home/debora/Documents/numeros"])])
t1.add_dataset(t1_input)
t1.begin()
#Leitura dos números do arquivo. No entanto, pulei esse código e coloquei direto.
PRIMEIRO_NUMERO = 5
SEGUNDO_NUMERO = 1
t1_output= DataSet("oExtrairNumeros", [Element([PRIMEIRO_NUMERO, SEGUNDO_NUMERO])])
t1.add_dataset(t1_output)
t1.end()
time.sleep(5)
t2 = Task(2, dataflow_tag, "ExecutarSoma", dependency=t1)
t2.begin()
RESULTADO_SOMA = PRIMEIRO_NUMERO + SEGUNDO_NUMERO
t2_output= DataSet("oExecutarSoma", [Element([RESULTADO_SOMA])])
t2.add_dataset(t2_output)
t2.end()
