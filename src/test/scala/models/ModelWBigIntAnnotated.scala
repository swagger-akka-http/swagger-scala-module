package models

import io.swagger.annotations.ApiModelProperty
import scala.annotation.meta.field

case class ModelWBigIntAnnotated(@(ApiModelProperty @field)(
  required = true, example = "42.0", dataType = "string", value="bigInt attribute") field: BigInt)
