package models

case class ModelWSeqString(strings: Seq[String])

case class ModelWSetString(strings: Set[String])

case class ModelWJavaListString(strings: java.util.List[String])

case class ModelWMapString(strings: Map[String, String])

case class ModelWJavaMapString(strings: java.util.Map[String, String])
