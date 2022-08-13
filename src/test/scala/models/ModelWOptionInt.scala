package models

import io.swagger.v3.oas.annotations.media.Schema

case class ModelWOptionInt(optInt: Option[Int])

object NestingObject {
  case class NestedModelWOptionInt(optInt: Option[Int])

  @Schema(description = "An empty case class")
  case class NoProperties()

  object NestedModelWOptionInt {

    def apply(nonOptional: Int): NestedModelWOptionInt = {
      NestedModelWOptionInt(Some(nonOptional))
    }
  }

  case class NestedModelWOptionIntSchemaOverride(@Schema(description = "This is an optional int") optInt: Option[Int])
}

case class ModelWOptionIntSchemaOverride(@Schema(description = "This is an optional int") optInt: Option[Int])

case class ModelWOptionIntSchemaOverrideForRequired(
    requiredInt: Int,
    requiredIntWithDefault: Int = 5,
    optionalInt: Option[Int],
    @Schema(description = "should stay required") annotatedRequiredInt: Int,
    @Schema(description = "should become optional") annotatedRequiredIntWithDefault: Int = 5,
    @Schema(description = "annotated default", defaultValue = "10") annotatedIntWithDefault: Int,
    @Schema(description = "should become required", required = true) annotatedOptionalInt: Option[Int]
)
