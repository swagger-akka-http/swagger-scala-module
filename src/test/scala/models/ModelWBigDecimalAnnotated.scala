package models

import io.swagger.annotations.ApiModelProperty

import scala.annotation.meta.field

case class ModelWBigDecimalAnnotated(@(ApiModelProperty @field)(
  required = true, example = "42", dataType = "string", value="bigDecimal attribute") field: BigDecimal)
