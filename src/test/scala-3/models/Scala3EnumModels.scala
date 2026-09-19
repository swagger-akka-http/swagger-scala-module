package models

import io.swagger.v3.oas.annotations.media.Schema

enum ColorEnum { case Red, Green, Blue }

case class Car(make: String, color: ColorEnum)

case class Colors(set: Set[ColorEnum])

case class CarWOptionColor(make: String, color: Option[ColorEnum])

case class CarWDefaultColor(make: String, color: ColorEnum = ColorEnum.Red)

case class CarWAnnotatedColor(
    make: String,
    @Schema(
      description = "paint colour",
      deprecated = true,
      example = "Green",
      defaultValue = "Blue",
      accessMode = Schema.AccessMode.READ_ONLY,
      requiredMode = Schema.RequiredMode.REQUIRED
    ) color: ColorEnum
)

case class CarWTypeOverriddenColor(
    make: String,
    @Schema(description = "free text colour", `type` = "string", requiredMode = Schema.RequiredMode.REQUIRED) color: ColorEnum
)

object Ctx {
  enum ColorEnum { case Red, Green, Blue }
}

case class CtxCar(make: String, color: Ctx.ColorEnum)

/** An enum with a parameterized case has no `values` method, so its singleton cases are discovered via `fromOrdinal`. */
enum AdtColor(val rgb: Int):
  case Red extends AdtColor(0xff0000)
  case Green extends AdtColor(0x00ff00)
  case Blue extends AdtColor(0x0000ff)
  case Mix(mix: Int) extends AdtColor(mix)

case class AdtColorSet(set: Set[AdtColor])

case class AdtCar(make: String, color: AdtColor)
