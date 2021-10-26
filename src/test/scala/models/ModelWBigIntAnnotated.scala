package models

import io.swagger.v3.oas.annotations.media.Schema

case class ModelWBigIntAnnotated(@Schema(description = "bigint value", `type` = "string", example = "42", required = true) field: BigInt)
