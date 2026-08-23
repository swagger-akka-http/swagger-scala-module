package com.github.swagger.scala.converter

import models._
import models.NestingObject.{NestedModelWOptionInt, NestedModelWOptionIntSchemaOverride, NoProperties}

/** Registers the type information of the test models with [[ScalaModelRegistry]].
  *
  * This is what a Scala 3 user of this module has to do for their own models; on Scala 2 these calls are no-ops because `scala-reflect`
  * reads the same information from the class files at runtime.
  */
object TestModelRegistration {
  private var registered = false

  def register(): Unit = synchronized {
    if (!registered) {
      registered = true
      ScalaModelRegistry.register[AddRequest]
      ScalaModelRegistry.register[AddRequestOldStyleAnnotation]
      ScalaModelRegistry.register[Animal]
      ScalaModelRegistry.register[Cat]
      ScalaModelRegistry.register[CoreBug814]
      ScalaModelRegistry.register[CustomCollection[String]]
      ScalaModelRegistry.register[DataExampleClass]
      ScalaModelRegistry.register[Dog]
      ScalaModelRegistry.register[EchoList]
      ScalaModelRegistry.register[ExampleProperties]
      ScalaModelRegistry.register[FeatureExample[String]]
      ScalaModelRegistry.register[ListReply[String]]
      ScalaModelRegistry.register[ModelWBigDecimalAnnotated]
      ScalaModelRegistry.register[ModelWBigDecimalAnnotatedDefault]
      ScalaModelRegistry.register[ModelWBigDecimalAnnotatedDefaultRequiredFalse]
      ScalaModelRegistry.register[ModelWBigDecimalAnnotatedNoType]
      ScalaModelRegistry.register[ModelWBigDecimalNoType]
      ScalaModelRegistry.register[ModelWBigIntAnnotated]
      ScalaModelRegistry.register[ModelWDuration]
      ScalaModelRegistry.register[ModelWEnumAnnotated]
      ScalaModelRegistry.register[ModelWGetFunction]
      ScalaModelRegistry.register[ModelWGetFunctionWithOptionalField]
      ScalaModelRegistry.register[ModelWIntMapLong]
      ScalaModelRegistry.register[ModelWJacksonAnnotatedGetFunction]
      ScalaModelRegistry.register[ModelWJavaListString]
      ScalaModelRegistry.register[ModelWJavaMapString]
      ScalaModelRegistry.register[ModelWMapIntLong]
      ScalaModelRegistry.register[ModelWMapString]
      ScalaModelRegistry.register[ModelWMapStringCaseClass]
      ScalaModelRegistry.register[ModelWMapStringLong]
      ScalaModelRegistry.register[ModelWMultipleRequiredFields]
      ScalaModelRegistry.register[ModelWOptionBigDecimal]
      ScalaModelRegistry.register[ModelWOptionBigInt]
      ScalaModelRegistry.register[ModelWOptionBoolean]
      ScalaModelRegistry.register[ModelWOptionBooleanSchemaOverride]
      ScalaModelRegistry.register[ModelWOptionInt]
      ScalaModelRegistry.register[ModelWOptionIntSchemaOverride]
      ScalaModelRegistry.register[ModelWOptionIntSchemaOverrideForRequired]
      ScalaModelRegistry.register[ModelWOptionLong]
      ScalaModelRegistry.register[ModelWOptionLongSchemaIntOverride]
      ScalaModelRegistry.register[ModelWOptionLongSchemaOverride]
      ScalaModelRegistry.register[ModelWOptionLongWithSomeDefault]
      ScalaModelRegistry.register[ModelWOptionModel]
      ScalaModelRegistry.register[ModelWOptionString]
      ScalaModelRegistry.register[ModelWOptionStringSeq]
      ScalaModelRegistry.register[ModelWOptionStringSeqAnnotated]
      ScalaModelRegistry.register[ModelWSeqInt]
      ScalaModelRegistry.register[ModelWSeqIntAnnotated]
      ScalaModelRegistry.register[ModelWSeqIntAnnotatedOldStyle]
      ScalaModelRegistry.register[ModelWSeqIntDefaulted]
      ScalaModelRegistry.register[ModelWSeqString]
      ScalaModelRegistry.register[ModelWSetString]
      ScalaModelRegistry.register[ModelWStringSeqAnnotated]
      ScalaModelRegistry.register[ModelWithJavaEnum]
      ScalaModelRegistry.register[ModelWithOptionAndNonOption]
      ScalaModelRegistry.register[ModelWithOptionAndNonOption2]
      ScalaModelRegistry.register[ModelWithOptionAndNonOption3]
      ScalaModelRegistry.register[ModelWithTestEnum]
      ScalaModelRegistry.register[NestedModelWOptionInt]
      ScalaModelRegistry.register[NestedModelWOptionIntSchemaOverride]
      ScalaModelRegistry.register[NoProperties]
      ScalaModelRegistry.register[PetOwner]
      ScalaModelRegistry.register[SModelWithEnum]
      ScalaModelRegistry.register[SModelWithEnumJacksonAnnotated]
      ScalaModelRegistry.register[SomeCaseClass]
      ScalaModelRegistry.registerAll[(ModelWithVector, ModelWithIntVector, ModelWithBooleanVector)]
      ScalaModelRegistry.registerAll[(OptionLong, OptionSeqLong, OptionSeqOptionLong, OptionSetString, SeqOptionLong)]
      ScalaModelRegistry.register[Nested.OptionSeqLong]
      ScalaModelRegistry.register[SimpleUser]
    }
  }
}
