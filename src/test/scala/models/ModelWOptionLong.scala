package models

import io.swagger.v3.oas.annotations.media.Schema

case class ModelWOptionLong(optLong: Option[Long])

case class ModelWOptionLongSchemaOverride(@Schema(implementation = classOf[Long]) optLong: Option[Long])
