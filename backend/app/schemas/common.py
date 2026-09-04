from pydantic import BaseModel, ConfigDict, Field


class ORMModel(BaseModel):
    model_config = ConfigDict(from_attributes=True)


class Page[T](BaseModel):
    items: list[T]
    total: int
    page: int
    per_page: int

    @property
    def pages(self) -> int:
        return max(1, -(-self.total // self.per_page))


class PageParams(BaseModel):
    page: int = Field(1, ge=1)
    per_page: int = Field(25, ge=1, le=100)
    search: str | None = Field(None, max_length=120)

    @property
    def offset(self) -> int:
        return (self.page - 1) * self.per_page


class Message(BaseModel):
    message: str
