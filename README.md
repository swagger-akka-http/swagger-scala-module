# Swagger Scala Module

![Build Status](https://github.com/swagger-akka-http/swagger-scala-module/actions/workflows/ci.yml/badge.svg)
[![Maven Central](https://maven-badges.sml.io/sonatype-central/com.github.swagger-akka-http/swagger-scala-module_2.13/badge.svg?style=plastic)](https://maven-badges.sml.io/sonatype-central/com.github.swagger-akka-http/swagger-scala-module_2.13)

This is a fork of https://github.com/swagger-api/swagger-scala-module.

| Release | Supports |
| ------- | -------- |
| 2.16.x | Scala 3 builds no longer depend on `scala3-reflection` - see section on Scala 3 type information below. Jackson 2.22. |
| 2.15.x | Jackson 2.21. |
| 2.14.x | Jackson 2.19. |
| 2.13.x | Jackson 2.18. |
| 2.12.x | Jackson 2.16. |
| 2.11.x | Jackson 2.15. |
| 2.10.x | Scala 3.3 support. Jackson 2.14. |
| 2.9.x | Refactor to support Swagger Schema annotation requiredMode. Jackson 2.14. |
| 2.8.x | Builds on the 2.7.x changes. Jackson 2.14. |
| 2.7.x | Scala 2 builds reintroduce scala-reflect dependency and can now introspect better on inner types. See section on `Treatment of Option` below. This has turned into a series with many experimental changes. It is probably best to upgrade to 2.8.x or later releases. |
| 2.6.x/2.5.x | First releases to support Scala 3. Jackson 2.13, [jakarta](https://github.com/swagger-api/swagger-core/wiki/Swagger-2.X---Getting-started) namespace jars. [OpenAPI 3.0.1](https://github.com/OAI/OpenAPI-Specification) / [Swagger-Core](https://github.com/swagger-api/swagger-core) 2.0.x. |
| 2.4.x | First releases to support [jakarta](https://github.com/swagger-api/swagger-core/wiki/Swagger-2.X---Getting-started) namespace jars. Jackson 2.12, [OpenAPI 3.0.1](https://github.com/OAI/OpenAPI-Specification) / [Swagger-Core](https://github.com/swagger-api/swagger-core) 2.0.x. |
| 2.3.x | [OpenAPI 3.0.1](https://github.com/OAI/OpenAPI-Specification) / [Swagger-Core](https://github.com/swagger-api/swagger-core) 2.0.x. |
| 1.3.0 | [Swagger Specification 2](https://swagger.io/specification/v2/) / [Swagger-Core](https://github.com/swagger-api/swagger-core) 1.6.x. |

## Usage
To enable the swagger-scala-module, include the appropriate version in your project:

```
  "com.github.swagger-akka-http" %% "swagger-scala-module" % "2.13.0"
```

## How does it work?
Including the library in your project allows the swagger extension module to discover this module, bringing in the appropriate jackson library in the process.  You can then use scala classes and objects in your swagger project.

## Treatment of `Option` and `required`

When users add swagger annotations (Schema, ArraySchema, Parameter), they can override whether a model property is required or not. Whether a model property is required or not, is usually based on the type of the related Scala field (i.e. fields of type `Option[T]` are optional - other fields are required).

The annotations mentioned above have a `required()` setting which is boolean and defaults to false. It is impossible to know if the user explicitly set false or if the are using the annotations to override other settings but don't intend to affect the required setting.

Swagger v2.2.5 introduces a `requiredMode()` setting on the Schema and ArraySchema annotations. The modes are Required, Not_Required and Auto - the latter is the default. This is better - but the `required()` setting, while deprecated, is still there and is set independently of the `requiredMode()`.

The treatment of `required()=false` prior to v2.9 is described in the below. [SwaggerScalaModelConverter.setRequiredBasedOnAnnotation](https://github.com/swagger-akka-http/swagger-scala-module/blob/bf97024492d07d7a293f72e4f113e9f378465bc2/src/main/scala/com/github/swagger/scala/converter/SwaggerScalaModelConverter.scala#L44) and [SwaggerScalaModelConverter.setRequiredBasedOnDefaultValue](https://github.com/swagger-akka-http/swagger-scala-module/blob/bf97024492d07d7a293f72e4f113e9f378465bc2/src/main/scala/com/github/swagger/scala/converter/SwaggerScalaModelConverter.scala#L58) will continue to affect how `required()=false` is interpreted.

| requiredMode() | required() | Effect on Model Property |
| ------------ | ------------ | ------------ |
| Required | this value is ignored | Model property is overridden to be required. |
| NotRequired | this value is ignored | Model property is overridden to be not required. |
| Auto (default) | true | Model property is overridden to be required. |
| Auto (default) | false (default) | if SwaggerScalaModelConverter.setRequiredBasedOnAnnotation is true (current default), then the model property is overridden to be required. Otherwise, whether the model property is required is not depends on the data type and whether a default value is set. |

Aim is to change the default for if SwaggerScalaModelConverter.setRequiredBasedOnAnnotation to false in a release in a few months time. I'm afraid that some existing users will get unexpected changes in behaviour if these changes are rolled out too quickly.

### Prior to v2.9

Prior to v2.7 releases, all properties, besides those wrapped in `Option` or explicitly set via annotations `@Schema(required = false, implementation = classOf[Int])`, default to `required = true`  in the generated swagger model. See [#7](https://github.com/swagger-api/swagger-scala-module/issues/7)

With Collections (and Options), scala primitives are affected by type erasure. You may need to declare the type using a Schema annotation.
```
case class AddOptionRequest(number: Int, @Schema(required = false, implementation = classOf[Int]) number2: Option[Int] = None)
```

Alternatively, you can use non-primitive types like BigInt to avoid this requirement.

Since the v2.7 releases, Scala 2 builds use scala-reflect jar to try to work out the class information for the inner types. See https://github.com/swagger-akka-http/swagger-scala-module/issues/117. Scala 3 builds used a runtime reflection lib for this up to v2.15.x - since v2.16.0, they read it at compile time, as described in the section on Scala 3 type information below.

v2.7 takes default values into account - either those specified in Scala contructors or via swagger annotations. A field might be marked as not required if a default value is specified.
If you don't use swagger annotations, and would like not like to infer the `required` value based on the default value, then you can set `SwaggerScalaModelConverter.setRequiredBasedOnDefaultValue` to `false`

If you use swagger annotations and don't want to explicity set the `required` value and allow this lib to infer the value, then you can set [SwaggerScalaModelConverter.setRequiredBasedOnAnnotation](https://github.com/swagger-akka-http/swagger-scala-module/blob/bf97024492d07d7a293f72e4f113e9f378465bc2/src/main/scala/com/github/swagger/scala/converter/SwaggerScalaModelConverter.scala#L44).

## Sealed Traits

Since v2.7.5, swagger-scala-module tries to handle sealed traits and classes. If an API method uses a sealed trait/class as a parameter or return type, the OpenAPI model for that type should be a schema with an `anyOf` construct that contains the schemas for all the classes that extend the selaed trait/class.

On Scala 3, the sealed trait/class needs to derive `ScalaTypeInfo` or be registered with `ScalaModelRegistry` - see the next section.

## Scala 3 type information

Two pieces of information that this module needs are not in the class files that Scala 3 produces:

* the element types of generic types - `Option[Int]` is compiled to `Option<Object>`, so the schema for such a field would be an object rather than an integer
* sealed hierarchies - Scala 3 does not record them in the class file, so a sealed trait would not get an `anyOf` schema

That information only exists in the TASTy files, which is why releases up to v2.15.x depended on `scala3-reflection` (and, through it, on `scala3-compiler`) to read it at runtime. Since v2.16.0, the module has no dependencies beyond `scala3-library` and reads the same information at compile time with a macro.

The simplest way to make it available is to derive `ScalaTypeInfo` on the model class. Nothing else needs to be called - the `derives` clause puts a given instance in the companion object of the class, and the module looks it up the first time it generates a schema for that class:

```scala
import com.github.swagger.scala.converter.ScalaTypeInfo

case class Pet(name: String, age: Option[Int]) derives ScalaTypeInfo

sealed trait Animal derives ScalaTypeInfo
case class Dog(name: String) extends Animal
case class Cat(name: String, lives: Option[Int]) extends Animal
```

For classes that cannot be given a `derives` clause - those of a third party library, for instance - register them instead, before any schema is generated:

```scala
import com.github.swagger.scala.converter.ScalaModelRegistry

ScalaModelRegistry.register[PetOwner]
ScalaModelRegistry.registerAll[(Order, Invoice)]
```

Both are recursive: the field types, collection element types, base classes and sealed subtypes of the type are covered too, so in practice only the top level model classes of your API need a `derives` clause or a `register` call.

Classes with type parameters cannot be derived - the compiler asks for a `ScalaTypeInfo` of each type parameter - and their element types are not known for the class as a whole anyway.

Classes that are neither derived nor registered still get schemas - the `Option` and collection fields whose element type is a Scala primitive (`Int`, `Long`, `Double`, `Boolean` and so on) are typed as objects, and sealed hierarchies do not get an `anyOf` schema. A warning is logged the first time such a class is seen. As before, you can also avoid the issue for a given field by annotating it, e.g. `@Schema(implementation = classOf[Int])`, or by using non-primitive types such as `BigInt`.

`derives` is Scala 3 syntax, so cross built code needs `ScalaModelRegistry`, which exists in the Scala 2 build too with all of its methods no-ops - Scala 2 reads the same information from the class files at runtime using `scala-reflect`.

## License

Copyright 2016 SmartBear Software, Inc.

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at [apache.org/licenses/LICENSE-2.0](http://www.apache.org/licenses/LICENSE-2.0)

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
