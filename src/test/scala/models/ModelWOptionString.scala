package models

import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.media.Schema.RequiredMode

case class ModelWOptionString(
    stringOpt: Option[String],
    stringWithDataTypeOpt: Option[String],
    @Schema(description = "An IP address", format = "IPv4 or IPv6")
    ipAddress: Option[String]
)

case class ModelWOptionModel(modelOpt: Option[ModelWOptionString])

case class ModelWithOptionAndNonOption(
    required: String,
    optional: Option[String],
    @Parameter(required = false) forcedOptional: String,
    @Parameter(required = true) forcedRequired: Option[String]
)

case class ModelWithOptionAndNonOption2(
    required: String,
    optional: Option[String],
    @Schema(requiredMode = RequiredMode.NOT_REQUIRED, implementation = classOf[String]) forcedOptional: String,
    @Schema(requiredMode = RequiredMode.REQUIRED, implementation = classOf[String]) forcedRequired: Option[String]
)

case class ModelWithOptionAndNonOption3(
    required: String,
    optional: Option[String],
    @Schema(required = false, implementation = classOf[String]) forcedOptional: String,
    @Schema(required = true, implementation = classOf[String]) forcedRequired: Option[String]
)
