package com.github.swagger.scala.converter

import io.swagger.v3.oas.models.media.{IntegerSchema, ObjectSchema, Schema}

private[converter] object SchemaModifier {
  def convertSchema(s: Schema[_], innerClass: Class[_]): Schema[_] = {
    if (innerClass.isAssignableFrom(classOf[Long])) {
      val is = new IntegerSchema
      is.setFormat("int64")
      copySchemaProperties(s, is)
      is
    } else if (innerClass.isAssignableFrom(classOf[Long])) {
      val is = new IntegerSchema
      is.setFormat("int32")
      copySchemaProperties(s, is)
      is
    } else {
      s
    }
  }

  private def copySchemaProperties(from: Schema[_], to: Schema[_]): Unit ={
    to.setRequired(from.getRequired)
    to.setType(from.getType)
    to.set$ref(from.get$ref())
    to.setProperties(from.getProperties)
    to.setAdditionalProperties(from.getAdditionalProperties)
    to.setDiscriminator(from.getDiscriminator)
    to.setName(from.getName)
    to.setDescription(from.getDescription)
    to.setDefault(from.getDefault)
    to.setDeprecated(from.getDeprecated)
    to.setReadOnly(from.getReadOnly)
    to.setWriteOnly(from.getWriteOnly )
    to.setUniqueItems(from.getUniqueItems)
    to.setExample(from.getExample)
    to.setExampleSetFlag(from.getExampleSetFlag)
    to.setExclusiveMinimum(from.getExclusiveMinimum)
    to.setExclusiveMaximum(from.getExclusiveMaximum)
    to.setExtensions(from.getExtensions)
    to.setExternalDocs(from.getExternalDocs)
    to.setMinimum(from.getMinimum)
    to.setMaximum(from.getMaximum)
    to.setMinLength(from.getMinLength)
    to.setMaxLength(from.getMaxLength)
    to.setMinItems(from.getMinItems)
    to.setMaxItems(from.getMaxItems)
    to.setMinProperties(from.getMinProperties)
    to.setMaxProperties(from.getMaxProperties)
    to.setMultipleOf(from.getMultipleOf)
    to.setNot(from.getNot)
    to.setNullable(from.getNullable)
    to.setPattern(from.getPattern)
    to.setXml(from.getXml)
    //to.setEnum(from.getEnum)
  }
}
