package models

import io.swagger.annotations.ApiModelProperty

import scala.annotation.meta.field

case class ModelWEnumAnnotated(@(ApiModelProperty @field)(
  required = true, example = "VENTI", dataType = "string", value="enum value") field: OrderSize.Value)
