package models

import io.swagger.v3.oas.annotations.media.Schema

case class ModelWOptionBoolean(optBoolean: Option[Boolean])

case class ModelWOptionBooleanSchemaOverride(@Schema(implementation = classOf[Boolean]) optBoolean: Option[Boolean])
