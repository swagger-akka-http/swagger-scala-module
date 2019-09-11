package models

import com.fasterxml.jackson.core.`type`.TypeReference
import com.fasterxml.jackson.module.scala.JsonScalaEnumeration
import io.swagger.v3.oas.annotations.media.Schema
import models.OrderSize.OrderSize

import scala.annotation.meta.field

@Schema(description = "Scala model containing an Enumeration Value that is annotated with the dataType of the Enumeration class")
case class SModelWithEnum(
  @(Schema @field)(name = "Order Size", implementation = classOf[OrderSize]) orderSize: OrderSize = OrderSize.TALL)

class OrderSizeTypeClass extends TypeReference[OrderSize.type]
case class SModelWithEnumJacksonAnnotated(
  @JsonScalaEnumeration(classOf[OrderSizeTypeClass]) orderSize: OrderSize = OrderSize.TALL)

case object OrderSize extends Enumeration {
  type OrderSize = Value
  val TALL = Value("TALL")
  val GRANDE = Value("GRANDE")
  val VENTI = Value("VENTI")
}