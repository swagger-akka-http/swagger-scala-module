package models

import io.swagger.v3.oas.annotations.media.{ArraySchema, Schema}

case class AddRequest(@ArraySchema(items = new Schema(implementation = classOf[Long]), minItems = 2, maxItems = 10)
                      numbers: Array[Double])
