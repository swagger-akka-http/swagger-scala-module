package models

import io.swagger.v3.oas.annotations.media.Schema

/**
 * [[Schema]] annotation has default value for [[Schema#required()]] property=false
 * It will overwrite by default required fields with required=false once annotation Schema will be applied without
 * explicitly marking @Schema(required=true)
 *
 * @param requiredField
 */
case class ModelWRequiredField(@Schema(description = "required field without schema property required defined") requiredField: Int)
