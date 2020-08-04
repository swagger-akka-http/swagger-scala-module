package models

import io.swagger.v3.oas.annotations.media.{ArraySchema, Schema}

case class ModelWStringSeq(@ArraySchema(arraySchema = new Schema(required = false), schema = new Schema(implementation = classOf[String])) listOfStrings: Seq[String] = Seq.empty[String])

case class ModelWOptionStringSeq(@ArraySchema(arraySchema = new Schema(required = true), schema = new Schema(implementation = classOf[String])) listOfStrings: Option[Seq[String]] = Some(Seq.empty[String]))
