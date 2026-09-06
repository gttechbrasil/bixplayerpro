"""HTTP error helpers. User-facing messages are in Portuguese."""

from typing import Any

from fastapi import HTTPException, status


class ApiError(HTTPException):
    def __init__(
        self,
        status_code: int,
        message: str,
        code: str | None = None,
        headers: dict[str, str] | None = None,
        **extra: Any,
    ):
        detail: dict[str, Any] = {"message": message}
        if code:
            detail["code"] = code
        detail.update(extra)
        super().__init__(status_code=status_code, detail=detail, headers=headers)


def bad_request(message: str, code: str = "bad_request", **extra: Any) -> ApiError:
    return ApiError(status.HTTP_400_BAD_REQUEST, message, code, **extra)


def unauthorized(message: str = "Não autenticado.", code: str = "unauthorized") -> ApiError:
    return ApiError(status.HTTP_401_UNAUTHORIZED, message, code)


def forbidden(message: str = "Acesso negado.", code: str = "forbidden") -> ApiError:
    return ApiError(status.HTTP_403_FORBIDDEN, message, code)


def not_found(message: str = "Registro não encontrado.", code: str = "not_found") -> ApiError:
    return ApiError(status.HTTP_404_NOT_FOUND, message, code)


def conflict(message: str, code: str = "conflict") -> ApiError:
    return ApiError(status.HTTP_409_CONFLICT, message, code)


def too_many_requests(message: str = "Muitas tentativas. Aguarde alguns minutos.") -> ApiError:
    return ApiError(status.HTTP_429_TOO_MANY_REQUESTS, message, "rate_limited")
