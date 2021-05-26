package pipelines4s.graalvm

import pipelines4s.common.{Arch, Platform}
import typings.azurePipelinesTaskLib.{mod => taskLib}
import typings.azurePipelinesTaskLib.mod.TaskResult
import typings.azurePipelinesToolLib.{toolMod => toolLib}
import typings.node.{fsMod => fs, pathMod => path}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.scalajs.js.annotation.JSExportTopLevel

@JSExportTopLevel("SetupGraalvm", "setup-graalvm")
object SetupGraalvm {

  def downloadGraalDistribution(inputs: Inputs): Future[String] = {
    import inputs._

    val downloadUrl = {
      val platformComponent = platform match {
        case Platform.Linux => "linux"
        case Platform.MacOS => "darwin"
        case Platform.Windows => "windows"
        case platform => throw new RuntimeException(s"Unsupported Platform: '$platform'. GraalVM CE supports only the following hosts at this time: ${Platform.known.mkString(", ")}")
      }
      val archComponent = {
        if (arch == Arch.Arm64 && platform != Platform.Linux) throw new RuntimeException("GraalVM CE only supports 'arm64' (AArch64) runtime architectures when running on Linux hosts at this time.")
        arch match {
          case Arch.X64 => "amd64"
          case Arch.Arm64 => "aarch64"
          case _ => throw new RuntimeException(s"Unsupported Runtime Architecture: '$arch'. GraalVM CE supports only the following hosts at this time: ${Arch.supported.filterNot(_ == Arch.X32).mkString(", ")}")
        }
      }
      val extension = if (platform == Platform.Windows) "zip" else "tar.gz"
      s"https://github.com/graalvm/graalvm-ce-builds/releases/download/vm-$graalVersion/graalvm-ce-java$javaVersion-$platformComponent-$archComponent-$graalVersion.$extension"
    }

    taskLib.debug(s"Downloading GraalVM distribution for $platform-$arch from: $downloadUrl")
    val downloaded: Future[String] = toolLib.downloadTool(downloadUrl).toFuture


    val extracted: Future[String] = downloaded.flatMap { downloaded =>
      val parent = if (platform == Platform.Windows) toolLib.extractZip(downloaded).toFuture else toolLib.extractTar(downloaded).toFuture
      def base = parent.map(parent => path.join(parent, s"graalvm-ce-java$javaVersion-$graalVersion"))
      if (platform == Platform.MacOS) base.map(base => path.join(base, "Contents")) else base
    }

    val cached = extracted.flatMap(extracted => toolLib.cacheDir(extracted, s"graalvm-ce-java$javaVersion", graalVersion.short).toFuture)

    cached.foreach { cached => taskLib.debug(s"Cached GraalVM $graalVersion distribution locally at $cached. Contents: ${fs.readdirSync(cached).join(", ")}") }

    cached
  }

  def installComponents(inputs: Inputs, binDir: String): Future[Unit] = {

    def install(components: Set[GraalComponent]): Unit = {
      val gu = path.join(binDir, "gu")
      components.foreach { component =>
        val args = s" -A -N install -n ${component.id}"
        taskLib.debug(s"Installing GraalVM '${component.id}' component using '$gu''")
        val result = taskLib.execSync(gu, args)
        if (result.code != 0) throw new RuntimeException(s"Error installing GraalVM '$component' component. ${result.stdout + result.stderr}'")
      }
    }

    val components: Future[Set[GraalComponent]] = GraalComponent.filterCompatible(inputs, Set(
      inputs.nativeImage -> GraalComponent.NativeImage,
      inputs.llvmToolchain -> GraalComponent.LlvmToolchain,
      inputs.espresso -> GraalComponent.Espresso,
      inputs.nodejs -> GraalComponent.NodeJS,
      inputs.python -> GraalComponent.Python,
      inputs.ruby -> GraalComponent.Ruby,
      inputs.r -> GraalComponent.R,
      inputs.wasm -> GraalComponent.Wasm
    ).filter(_._1).map(_._2))

    components.map(install)
  }

  def run(inputs: Inputs): Unit = {
    import inputs._
    val tool = s"graalvm-ce-java$javaVersion"
    try {
      if (toolLib.findLocalToolVersions(tool).contains(graalVersion.short)) {
        val localPath = toolLib.findLocalTool(tool, graalVersion.short)
        val installedVersion = resolveInstalledVersion(resolveHome(localPath, platform))
        if (installedVersion == graalVersion) {
          taskLib.debug(s"Using GraalVM from '$localPath'.")
          val graalHome = resolveHome(localPath, inputs.platform)
          taskLib.debug(s"Setting 'GRAALVM_HOME' and 'JAVA_HOME' as '$graalHome'. Contents: ${fs.readdirSync(graalHome).join(", ")}")
          taskLib.setVariable("GRAALVM_HOME", graalHome)
          taskLib.setVariable("JAVA_HOME", graalHome)

          val binDir = path.join(graalHome, "bin")
          taskLib.debug(s"Using GraalVM bin directory at $binDir. Contents: ${fs.readdirSync(binDir).join(", ")}")
          taskLib.prependPath(binDir)

          taskLib.debug(s"Using locally cached GraalVM $graalVersion for Java $javaVersion from '$localPath'.")
          installComponents(inputs, binDir).foreach(_ => taskLib.debug(s"GraalVM $graalVersion for Java $javaVersion Setup complete."))
        } else {
          taskLib.debug(s"Locally cached GraalVM $installedVersion does not equal required $graalVersion.")
          downloadGraalDistribution(inputs).foreach(_ => run(inputs)
          )
        }
      } else {
        taskLib.debug(s"No locally cached GraalVM $graalVersion for Java $javaVersion found.")
        downloadGraalDistribution(inputs).foreach(_ => run(inputs))
      }
    } catch {
      case err: Throwable => taskLib.setResult(TaskResult.Failed, s"Error setting up GraalVM $graalVersion for Java $javaVersion on $arch. $err")
    }
  }

  private def resolveHome(root: String, platform: Platform) =
    if (platform == Platform.MacOS) path.join(root, "Home") else root

  private def resolveInstalledVersion(graalVmHome: String) = {
    val releaseFile = path.join(graalVmHome, "release")
    taskLib.debug(s"Verifying installed GraalVM version using 'release' file at $releaseFile")
    try {
      val Pattern = """GRAALVM_VERSION\s*=\s*"([1-9]\d*\.(?:0|[1-9]\d*)\.(?:0|[1-9]\d*)(?:\.(?:0|[1-9]\d*))?)"""".r
      val parsedVersion = GraalVersion(Pattern.findFirstMatchIn(fs.readFileSync(releaseFile).toString).map(_.group(1)).get)
      taskLib.debug(s"Resolved installed release version $parsedVersion at $graalVmHome")
      parsedVersion
    } catch {
      case err: Throwable => throw new RuntimeException(s"Error resolving installed GraalVM version. Unable to read version information from 'release' file at $releaseFile", err)
    }
  }

  run(Inputs())

}
