package models

import com.fasterxml.jackson.core.`type`.TypeReference
import io.swagger.annotations.{ApiModel, ApiModelProperty}
import models.OrderSize.OrderSize
import models.TestEnum.TestEnum

import scala.annotation.meta.field

@ApiModel(description = "Scala model containing an Enumeration Value that is annotated with the dataType of the Enumeration class")
case class SModelWithEnum(
  @(ApiModelProperty @field)(value = "Order Size", dataType = "models.OrderSize$") orderSize: OrderSize = OrderSize.TALL)

case object OrderSize extends Enumeration(0) {
  type OrderSize = Value
  val TALL = Value("TALL")
  val GRANDE = Value("GRANDE")
  val VENTI = Value("VENTI")
}

class TestEnumTypeClass extends TypeReference[TestEnum.type]
case class ModelWithTestEnum(@(ApiModelProperty @field)(value = "Order Size", dataType = "models.TestEnum$") testEnum: TestEnum = TestEnum.AEnum)

object TestEnum extends Enumeration {
  type TestEnum = Value
  val AEnum = Value("a")
  val BEnum = Value("b")
}

case class ModelWithJavaEnum(level: Level)
