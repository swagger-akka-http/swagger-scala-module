package models

case class ModelWSeqString(strings: Seq[String])

case class ModelWSetString(strings: Set[String])

case class ModelWJavaListString(strings: java.util.List[String])

case class ModelWMapString(strings: Map[String, String])

case class SomeCaseClass(field: Int)

case class ModelWMapStringCaseClass(maybeMapStringCaseClass: Option[Map[String, SomeCaseClass]])

case class ModelWMapStringLong(maybeMapStringLong: Option[Map[String, Long]])

case class ModelWJavaMapString(strings: java.util.Map[String, String])
