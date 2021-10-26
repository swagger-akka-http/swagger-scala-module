package models

import io.swagger.v3.oas.annotations.media.Schema

case class ModelWOptionInt(optInt: Option[Int])

case class ModelWOptionIntSchemaOverride(@Schema(implementation = classOf[Int]) optInt: Option[Int])
