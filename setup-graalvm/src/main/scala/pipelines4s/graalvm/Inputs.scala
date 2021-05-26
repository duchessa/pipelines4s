package pipelines4s.graalvm

import pipelines4s.common.{Arch, Platform}
import typings.azurePipelinesTaskLib.{mod => taskLib}
import typings.azurePipelinesTaskLib.mod.TaskResult

final case class Inputs(platform: Platform,
                        arch: Arch,
                        graalVersion: GraalVersion,
                        javaVersion: Int,
                        nativeImage: Boolean,
                        llvmToolchain: Boolean,
                        espresso: Boolean,
                        nodejs: Boolean,
                        python: Boolean,
                        ruby: Boolean,
                        r: Boolean,
                        wasm: Boolean) {
  if (graalVersion < GraalVersion.MinimumSupported)
    taskLib.setResult(TaskResult.Failed, s"GraalVM versions below ${GraalVersion.MinimumSupported} are not currently supported by pipelines4s. Configured version: $graalVersion")
}

object Inputs {

  def apply(): Inputs = Inputs(
    Platform(),
    Arch(),
    GraalVersion(taskLib.getInput("graalVersion", required = true).get),
    taskLib.getInput("javaVersion", required = true).get.toInt,
    taskLib.getBoolInput("nativeImage", required = true),
    taskLib.getBoolInput("llvmToolchain", required = true),
    taskLib.getBoolInput("espresso", required = true),
    taskLib.getBoolInput("nodejs", required = true),
    taskLib.getBoolInput("python", required = true),
    taskLib.getBoolInput("ruby", required = true),
    taskLib.getBoolInput("r", required = true),
    taskLib.getBoolInput("wasm", required = true)
  )

}
