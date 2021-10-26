package models

import io.swagger.v3.oas.annotations.media.Schema

case class ModelWBigDecimalAnnotated(@Schema(description = "bigdecimal value", `type` = "string", example = "42.0", required = true) field: BigDecimal)
