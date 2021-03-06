# pipelines4s

Azure DevOps Pipelines extension providing assorted tasks for compilation, testing, and deployment of Scala and/or Java based projects. Implemented
in Scala.js.

[![Build Status](https://dev.azure.com/duchessa/pipelines4s/_apis/build/status/duchessa.pipelines4s?branchName=main)](https://dev.azure.com/duchessa/pipelines4s/_build?definitionId=5)
[![Current Release](https://img.shields.io/github/v/tag/duchessa/pipelines4s?color=blue&label=Current%20Release)](https://github.com/duchessa/pipelines4s/tags)
[![Azure Pipelines Task](https://img.shields.io/badge/Extension%20Marketplace-Pipelines4s-blue?logo=azure-pipelines)](https://marketplace.visualstudio.com/items?itemName=duchessa.pipelines4s)


## Usage

Install this extension to your Azure DevOps Organisation from Azure DevOps [Extensions Marketplace](https://marketplace.visualstudio.com/items?itemName=duchessa.pipelines4s)
to make tasks provided by this extension available for use in organisation projects. Refer to task availability and configuration reference below.

## Included Tasks

- ### setup-graalvm (Version: 1.x)
  Task to search the local agent tool cache for a specified [GraalVM](https://graalvm.org) Community Edition distribution
  version; and prepend its binaries to the system PATH for use in subsequent build steps. If a cached installation matching
  the specified `graalVersion`  and targeted `javaVersion` settings is not found, then an attempt will be made to download
  and install it to the agent local tool cache.

  | Argument                | Description                                                                                                                                                                                                                   |
  | :---------------------- | :---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
  | `graalVersion`          | String defining version number of [GraalVM Community Edition](https://github.com/graalvm/graalvm-ce-builds/releases) to be installed.                                                                                         |
  | `javaVersion`           | Defining major version number of targeted [Java Platform](https://www.oracle.com/java/technologies/java-se-glance.html) specification. Limited to Java versions supported by the [GraalVM Project](https://www.graalvm.org/). |
  | `nativeImage`           | Boolean defining whether the GraalVM [Native Image](https://www.graalvm.org/reference-manual/native-image/) component is installed. Default is 'true'.                                                                        |
  | `llvmToolchain`         | Boolean defining whether the GraalVM [LLVM toolchain](https://www.graalvm.org/reference-manual/llvm/) component is installed. Default is 'false'.                                                                             |
  | `espresso`              | Boolean defining whether the GraalVM [Truffle Framework](https://www.graalvm.org/reference-manual/java-on-truffle/) component is installed. Default is 'false'.                                                               |
  | `nodejs`                | Boolean defining whether the GraalVM [Node.js](https://www.graalvm.org/reference-manual/js/#running-nodejs) component is installed. Default is 'false'.                                                                       |
  | `python`                | Boolean defining whether the GraalVM [Python](https://www.graalvm.org/reference-manual/python/) component is installed. Default is 'false'.                                                                                   |
  | `ruby`                  | Boolean defining whether the GraalVM [Ruby](https://www.graalvm.org/reference-manual/ruby/) component is installed. Default is 'false'.                                                                                       |
  | `r`                     | Boolean defining whether the GraalVM [R](https://www.graalvm.org/reference-manual/r/) component is installed. Default is 'false'.                                                                                             |
  | `wasm`                  | Boolean defining whether the GraalVM [WebAssembly](https://www.graalvm.org/reference-manual/wasm/) component is installed. Default is 'false'.                                                                                |

  #### Usage example:
  ```yaml
  steps:
    - task: setup-graalvm@1
      inputs:
        graalVersion: '21.0.0.2'
        javaVersion: '11'
        llvmToolchain: true
  ```

- ### setup-sbt (Version: 1.x)
  Task to search the local agent tool cache for a specified [sbt](https://www.scala-sbt.org/) launcher installation;
  and prepend its binaries to the system PATH for use in subsequent build steps. If a cached installation matching the
  specified `sbtVersion` setting is not found, then an attempt will be made to download and install it to the agent local
  tool cache.

  | Argument     | Description                                                                                            |
  | :----------- | :----------------------------------------------------------------------------------------------------- |
  | `sbtVersion` | String defining version number of [sbt](https://github.com/sbt/sbt/releases) launcher to be installed. |

  #### Usage example:
  ```yaml
  steps:
    - task: setup-sbt@1
      inputs:
        sbtVersion: '1.4.9'
  ```

_____________

## License
See the NOTICE file distributed with this work for additional information regarding copyright ownership

Licensed under the Apache License, Version 2.0 (the "License"); you may not use this work except in compliance
with the License. You may obtain a copy of the License at

[`https://www.apache.org/licenses/LICENSE-2.0`](https://www.apache.org/licenses/LICENSE-2.0)

Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for
the specific language governing permissions and limitations under the License.
