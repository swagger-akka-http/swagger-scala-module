package models

import io.swagger.v3.oas.annotations.media.Schema

case class ModelWOptionInt(optInt: Option[Int])

case class ModelWOptionIntSchemaOverride(@Schema(description = "This is an optional int") optInt: Option[Int])

case class ModelWOptionIntSchemaOverrideForRequired(@Schema(required = true) optInt: Option[Int])
