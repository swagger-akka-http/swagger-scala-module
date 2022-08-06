package models

import io.swagger.v3.oas.annotations.media.{ArraySchema, Schema}

case class ModelWSeqInt(ints: Seq[Int])

case class ModelWSeqIntAnnotated(@ArraySchema(arraySchema = new Schema(required = false), schema = new Schema(description = "These are ints")) ints: Seq[Int])
