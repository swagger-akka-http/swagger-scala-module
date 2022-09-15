package models

import com.fasterxml.jackson.annotation.JsonIgnore

case class ModelWGetFunction(amount: Long) {
  def getOptionalAmount(): Option[Long] = Some(amount)
}

case class ModelWJacksonAnnotatedGetFunction(amount: Long) {
  @JsonIgnore def getOptionalAmount(): Option[Long] = Some(amount)
}
