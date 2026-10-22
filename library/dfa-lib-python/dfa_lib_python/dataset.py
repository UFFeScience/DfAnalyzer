import warnings

from .ProvenanceObject import ProvenanceObject
from .element import Element

# Tamanho das colunas de texto criadas pelo backend (DataflowProvenance.java).
MAX_VALUE_LENGTH = 500


class ProvenanceValueWarning(UserWarning):
    """Um valor que o backend vai recusar ao gravar."""


class DataSet(ProvenanceObject):
    """
    This class defines a dataflow dataset.

    Attributes:
        - tag (str): Dataset tag.
        - elements (:obj:`Element`): Dataset Elements
    """

    def __init__(self, tag, elements):
        ProvenanceObject.__init__(self, tag)
        self.elements = elements

    @property
    def elements(self):
        """Get or set elements."""
        return self._elements

    @elements.setter
    def elements(self, elements):
        assert isinstance(elements, list), \
            "The elements must be in a list."
        result = []
        for element in elements:
            assert isinstance(element, Element), "The element must be valid."
            self._warn_values_too_long(element.values)
            result.append(element.values)
        self._elements = result

    def _warn_values_too_long(self, values):
        for position, value in enumerate(values):
            if len(value) > MAX_VALUE_LENGTH:
                warnings.warn(
                    "dataset '{}': o valor na posicao {} tem {} caracteres - acima do "
                    "limite de {} das colunas TEXT/FILE do DfAnalyzer; o servidor vai "
                    "grava-lo como NULL. Passe um resumo (ex.: str(df.shape), len(x)) "
                    "em vez do objeto inteiro. Inicio do valor: {!r}".format(
                        self._tag, position, len(value), MAX_VALUE_LENGTH, value[:80]),
                    ProvenanceValueWarning,
                    stacklevel=4,  # _warn -> setter -> __init__ -> codigo de quem chamou.
                )
