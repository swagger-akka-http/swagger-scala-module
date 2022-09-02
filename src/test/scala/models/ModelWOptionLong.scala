package models

import io.swagger.v3.oas.annotations.media.Schema

case class ModelWOptionLong(optLong: Option[Long])
case class ModelWOptionLongWithSomeDefault(optLong: Option[Long] = Some(Long.MaxValue))

case class ModelWOptionLongSchemaOverride(@Schema(implementation = classOf[Long]) optLong: Option[Long])

case class ModelWOptionLongSchemaIntOverride(@Schema(implementation = classOf[Int]) optLong: Option[Long])
