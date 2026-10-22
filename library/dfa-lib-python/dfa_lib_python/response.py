import warnings


class ProvenanceRejectedWarning(UserWarning):
    """O backend recusou um envio: o que foi enviado nao esta gravado."""


def check_response(response, what):
    """Avisa quando o backend responde com erro, sem interromper o programa instrumentado."""
    status = getattr(response, "status_code", None)
    if status is None or status < 400:
        return
    detail = (getattr(response, "text", "") or "").strip()[:500]
    warnings.warn(
        f"{what}: o DfAnalyzer respondeu {status} e NAO gravou este envio. {detail}",
        ProvenanceRejectedWarning,
        stacklevel=3,
    )
