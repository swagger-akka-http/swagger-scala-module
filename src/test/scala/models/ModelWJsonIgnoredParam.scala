package models

import com.fasterxml.jackson.annotation.JsonIgnore
import io.swagger.v3.oas.annotations.media.Schema

import scala.annotation.meta.field

case class ModelWJsonIgnoredParam(@JsonIgnore secret: String, name: String, count: Option[Int])

case class ModelWRenamedParam(@(Schema @field)(name = "renamed") value: String, other: Option[Int])
