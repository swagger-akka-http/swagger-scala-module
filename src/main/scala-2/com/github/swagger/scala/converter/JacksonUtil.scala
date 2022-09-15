package com.github.swagger.scala.converter

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.scala.DefaultScalaModule
import com.github.pjfanning.jackson.reflect.ScalaReflectAnnotationIntrospectorModule
import io.swagger.v3.core.util.Json

object JacksonUtil {
  val objectMapper: ObjectMapper = Json.mapper()
    .registerModule(DefaultScalaModule)
    .registerModule(ScalaReflectAnnotationIntrospectorModule)
}
