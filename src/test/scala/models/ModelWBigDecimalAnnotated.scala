package models

import io.swagger.v3.oas.annotations.media.Schema

case class ModelWBigDecimalAnnotated(
    @Schema(description = "bigdecimal value", `type` = "string", example = "42.0", required = true, deprecated = true) field: BigDecimal
)

case class ModelWBigDecimalNoType(field: BigDecimal)
case class ModelWBigDecimalAnnotatedNoType(@Schema(description = "should stay BigDecimal") field: BigDecimal)

case class ModelWBigDecimalAnnotatedDefault(
    @Schema(
      description = "required of annotation should be honoured",
      `type` = "string",
      example = "42.0",
      defaultValue = "42.0",
      required = true
    ) field: BigDecimal = BigDecimal.valueOf(0)
)

case class ModelWBigDecimalAnnotatedDefaultRequiredFalse(
    @Schema(
      description = "required of annotation should be honoured",
      `type` = "string",
      example = "42.0",
      defaultValue = "42.0",
      required = false
    ) field: BigDecimal = BigDecimal.valueOf(0)
)
