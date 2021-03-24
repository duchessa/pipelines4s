# pipelines4s
Azure DevOps Pipelines extension providing assorted tasks and utilities for compilation, testing,
and deployment of Scala and/or Java based projects.

## Included Tasks 
### setup-graalvm
Task to download, cache, configure and add to PATH a GraalVM Community Edition distribution.

| Argument                | Description                                                                                                                                                                                                                   |
| :---------------------- | :---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `graalVersion`          | String defining version number of [GraalVM Community Edition](https://github.com/graalvm/graalvm-ce-builds/releases) to be installed.                                                                                         |
| `javaVersion`           | Defining major version number of targeted [Java Platform](https://www.oracle.com/java/technologies/java-se-glance.html) specification. Limited to Java versions supported by the [GraalVM Project](https://www.graalvm.org/). |
| `prefixNodeExecutables` | Boolean defining whether GraalVM provided `npm`, `npx`, and `node` commands should be prepended with `graalvm-` to allow concurrent usage with existing NodeJS installation. Default is 'true'.                               |
| `nativeImage`           | Boolean defining whether the GraalVM [Native Image](https://www.graalvm.org/reference-manual/native-image/) component is installed. Default is 'true'.                                                                        |
| `llvmToolchain`         | Boolean defining whether the GraalVM [LLVM toolchain](https://www.graalvm.org/reference-manual/llvm/) component is installed. Default is 'false'.                                                                             |
  
#### Usage example:
```yaml
steps:
  - task: setup-graalvm@1
    inputs:
      graalVersion: '21.0.0.2'
      javaVersion: '11'
      llvmToolchain: true
```

### setup-sbt
Task to download, cache, and add to PATH an [sbt](https://www.scala-sbt.org/) launcher installation.

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
See the NOTICE file distributed with this work for additional
information regarding copyright ownership

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this work except in compliance with the License.
You may obtain a copy of the License at

[`https://www.apache.org/licenses/LICENSE-2.0`](https://www.apache.org/licenses/LICENSE-2.0)

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
