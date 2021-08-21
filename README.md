# Swagger Scala Module

![Build Status](https://github.com/swagger-akka-http/swagger-scala-module/actions/workflows/ci.yml/badge.svg)
[![Maven Central](https://maven-badges.herokuapp.com/maven-central/com.github.swagger-akka-http/swagger-scala-module_2.13/badge.svg?style=plastic)](https://maven-badges.herokuapp.com/maven-central/com.github.swagger-akka-http/swagger-scala-module_2.13)

This is a fork of https://github.com/swagger-api/swagger-scala-module. This version supports [OpenAPI](https://github.com/OAI/OpenAPI-Specification) 3.0.1 / [Swagger-Core](https://github.com/swagger-api/swagger-core) 2.0.4.

## Usage
To enable the swagger-scala-module, include the appropriate version in your project:

```
  "com.github.swagger-akka-http" %% "swagger-scala-module" % "2.3.1"
```

## How does it work?
Including the library in your project allows the swagger extension module to discover this module, bringing in the appropriate jackson library in the process.  You can then use scala classes and objects in your swagger project.

## Treatment of `Option` and `required`
All properties, besides those wrapped in `Option` or explicitly set via annotations `@ApiModelProperty(required = false)`, default to `required = true`  in the generated swagger model. See [#7](https://github.com/swagger-api/swagger-scala-module/issues/7)

License
-------

Copyright 2016 SmartBear Software, Inc.

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at [apache.org/licenses/LICENSE-2.0](http://www.apache.org/licenses/LICENSE-2.0)

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
