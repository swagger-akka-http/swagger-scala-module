package models

final case class DataExampleClass(data: CustomCollection[ExampleProperties])

final case class CustomCollection[T](features: Seq[FeatureExample[T]])

final case class FeatureExample[T](geometry: Option[T])

final case class ExampleProperties(p1: Int, p2: Double)
