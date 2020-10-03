package models

import io.swagger.v3.oas.annotations.media.Schema

case class ModelWEnumAnnotated(@Schema(description = "enum value", `type` = "string", required = true) field: OrderSize.Value)
