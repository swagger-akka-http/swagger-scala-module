# Swagger Scala Module

![Build Status](https://github.com/swagger-akka-http/swagger-scala-module/actions/workflows/ci.yml/badge.svg)
[![Maven Central](https://maven-badges.herokuapp.com/maven-central/com.github.swagger-akka-http/swagger-scala-module_2.13/badge.svg?style=plastic)](https://maven-badges.herokuapp.com/maven-central/com.github.swagger-akka-http/swagger-scala-module_2.13)

This is a fork of https://github.com/swagger-api/swagger-scala-module.

| Release | Supports |
| ------- | -------- |
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
  "com.github.swagger-akka-http" %% "swagger-scala-module" % "2.9.1"
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

Since the v2.7 releases, Scala 2 builds use scala-reflect jar to try to work out the class information for the inner types. Since v2.7.5, Scala 3 builds use a lib that supports runtime reflection. See https://github.com/swagger-akka-http/swagger-scala-module/issues/117. One issue affacting Scala 3 users is https://github.com/gzoller/scala-reflection/issues/40.

v2.7 takes default values into account - either those specified in Scala contructors or via swagger annotations. A field might be marked as not required if a default value is specified.
If you don't use swagger annotations, and would like not like to infer the `required` value based on the default value, then you can set `SwaggerScalaModelConverter.setRequiredBasedOnDefaultValue` to `false`

If you use swagger annotations and don't want to explicity set the `required` value and allow this lib to infer the value, then you can set [SwaggerScalaModelConverter.setRequiredBasedOnAnnotation](https://github.com/swagger-akka-http/swagger-scala-module/blob/bf97024492d07d7a293f72e4f113e9f378465bc2/src/main/scala/com/github/swagger/scala/converter/SwaggerScalaModelConverter.scala#L44).

## Sealed Traits

Since v2.7.5, swagger-scala-module tries to handle sealed traits and classes. If an API method uses a sealed trait/class as a parameter or return type, the OpenAPI model for that type should be a schema with an `anyOf` construct that contains the schemas for all the classes that extend the selaed trait/class. 

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
