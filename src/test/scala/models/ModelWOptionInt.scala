package models

import io.swagger.v3.oas.annotations.media.Schema

case class ModelWOptionInt(optInt: Option[Int])

object NestingObject {
  case class NestedModelWOptionInt(optInt: Option[Int])

  object NestedModelWOptionInt {

    def apply(nonOptional: Int): NestedModelWOptionInt = {
      NestedModelWOptionInt(Some(nonOptional))
    }
  }

  case class NestedModelWOptionIntSchemaOverride(@Schema(description = "This is an optional int") optInt: Option[Int])
}

case class ModelWOptionIntSchemaOverride(@Schema(description = "This is an optional int") optInt: Option[Int])

case class ModelWOptionIntSchemaOverrideForRequired(@Schema(required = true) optInt: Option[Int])
