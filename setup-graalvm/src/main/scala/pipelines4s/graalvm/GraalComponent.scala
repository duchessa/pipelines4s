package pipelines4s.graalvm

import pipelines4s.common.{Arch, Platform}
import typings.azurePipelinesTaskLib.{mod => taskLib}
import typings.azurePipelinesTaskLib.mod.TaskResult
import typings.typedRestClient.httpClientMod.HttpClient

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

final case class GraalComponent private(id: String) extends AnyVal

object GraalComponent {

  // Tools / Utilities
  val NativeImage: GraalComponent = GraalComponent("native-image")
  val LlvmToolchain: GraalComponent = GraalComponent("llvm-toolchain")
  val Espresso: GraalComponent = GraalComponent("espresso")

  // Runtimes
  val NodeJS: GraalComponent = GraalComponent("nodejs")
  val Python: GraalComponent = GraalComponent("python")
  val Ruby: GraalComponent = GraalComponent("ruby")
  val R: GraalComponent = GraalComponent("R")
  val Wasm: GraalComponent = GraalComponent("wasm")

  def components = Set(NativeImage, LlvmToolchain, Espresso, NodeJS, Python, Ruby, R, Wasm)

  def resolveCatalogueEntries(inputs: Inputs): Future[Set[GraalComponent]] = {

    def platformComponent =
      inputs.platform match { case Platform.Linux => "linux"; case Platform.Windows => "windows"; case Platform.MacOS => "macos" }

    def archComponent = inputs.arch match {
      case Arch.X64 => "amd64";
      case Arch.Arm64 => "aarch64"
      case _ => throw new RuntimeException("Unexpected Error.")
    }

    val ComponentCatalogPattern = {
      val base = s"""Component\\.($platformComponent)_($archComponent)\\/(${inputs.graalVersion.major}\\.${inputs.graalVersion.minor}\\.${inputs.graalVersion.patch}(?:\\.${inputs.graalVersion.update.getOrElse(0)})?)\\/[a-fA-F0-9]{64}\\/.+-Bundle-Symbolic-Name=org\\.graalvm\\.(.+)"""
      base.r
    }

    def catalogUrl = s"https://raw.githubusercontent.com/graalvm/graalvm-website/master/component-catalog/v2/graal-updater-component-catalog-java${inputs.javaVersion}.properties"

    val res = new HttpClient().get(catalogUrl).toFuture

    res.foreach(_.message.statusCode.toOption.foreach(code => if (code.toInt != 200) taskLib.setResult(TaskResult.Failed, s"Error fetching GraalVM component catalogue from: $catalogUrl")))

    res.flatMap(_.readBody().toFuture)
      .map {
        ComponentCatalogPattern.findAllMatchIn(_).map(entry => components.find(_.id == entry.group(4)).getOrElse {
          taskLib.warning(s"GraalVM component unsupported by pipelines4s. Component: ${entry.group(4)}")
          GraalComponent(entry.group(4))
        }).toSet
      }
  }

  def filterCompatible(inputs: Inputs, components: Set[GraalComponent]): Future[Set[GraalComponent]] = resolveCatalogueEntries(inputs).map { entries =>
    val incompatible = components.diff(entries)
    if (incompatible.nonEmpty)
      taskLib.warning(s"The following GraalVM Components are not compatible with the current GraalVM Version, Java Version, Operating System and/or Architecture: ${incompatible.map(_.id).mkString(", ")}")
    components -- incompatible
  }

}

