import typings.azurePipelinesTaskLib.{mod => taskLib}
import typings.azurePipelinesTaskLib.mod.{Platform, TaskResult}
import typings.azurePipelinesToolLib.{toolMod => toolLib}
import typings.node.{fsMod => fs, osMod => os, pathMod => path}

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
        case platform => throw new RuntimeException(s"Unsupported Platform: '$platform'. GraalVM CE supports only Linux, Darwin, or Windows hosts at this time.")
      }
      val archComponent = {
        if (arch == Arch.Arm64 && platform != Platform.Linux) throw new RuntimeException("GraalVM CE only supports 'arm64' (AArch64) runtime architectures when running on Linux hosts at this time.")
        if (arch == Arch.Arm64) "aarch64" else "amd64"
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

  def configureLocalTool(inputs: Inputs, localPath: String): Unit = {

    def configurePrefixedExecutables(graalBinDir: String): Unit = try {
      val prefixedExecutables = {
        val base = Seq("node", "npm", "npx")
        val bat = base.map(_ + ".bat")
        val cmd = base.map(_ + ".cmd")
        val exe = base.map(_ + ".exe")
        if (inputs.platform == Platform.Windows) base ++ bat ++ cmd ++ exe else base
      }
      val prefix = "graal-"

      val resolvedBinaries = if (inputs.prefixExecutables) fs.readdirSync(graalBinDir).filter(file => prefixedExecutables.contains(file)) else
        fs.readdirSync(graalBinDir).filter(file => prefixedExecutables.map(file => s"$prefix$file").contains(file))

      if (inputs.prefixExecutables) {
        taskLib.debug(s"Prefixing common GraalVM executables with $prefix")
        resolvedBinaries.foreach { f =>
          val oldPath = path.join(graalBinDir, f)
          val newPath = path.join(graalBinDir, s"$prefix$f")
          taskLib.debug(s"Renaming $oldPath to $newPath")
          fs.renameSync(oldPath, newPath)
        }
      } else {
        taskLib.debug(s"Removing '$prefix' prefix from  common GraalVM executables if existing.'")
        resolvedBinaries.foreach { f =>
          val oldPath = path.join(graalBinDir, f)
          val newPath = path.join(graalBinDir, f.substring(prefix.length))
          taskLib.debug(s"Renaming $oldPath to $newPath")
          fs.renameSync(oldPath, newPath)
        }
      }
    } catch {
      case err: Throwable => throw new RuntimeException(s"Error setting up GraalVM NodeJS executables prefix: Configured setting: prefixNodeExecutables := '${inputs.prefixExecutables}. $err")
    }

    def installComponents(graalBinDir: String, components: Seq[String]): Unit = {
      val gu = path.join(graalBinDir, "gu")
      components.foreach { component =>
        val args = s" -A -N install -n $component"
        taskLib.debug(s"Installing GraalVM '$component' component using '$gu''")
        val result = taskLib.execSync(gu, args)
        if (result.code != 0) throw new RuntimeException(s"Error installing GraalVM '$component' component. ${result.stdout + result.stderr}'")
      }
    }

    def components: Seq[String] = Seq(
      inputs.nativeImage -> "native-image",
      inputs.llvmToolchain -> "llvm-toolchain"
    ).filter(_._1).map(_._2)

    taskLib.debug(s"Using GraalVM from '$localPath'.")

    val graalHome = resolveHome(localPath, inputs.platform)
    taskLib.debug(s"Setting 'GRAALVM_HOME' and 'JAVA_HOME' as '$graalHome'. Contents: ${fs.readdirSync(graalHome).join(", ")}")
    taskLib.setVariable("GRAALVM_HOME", graalHome)
    taskLib.setVariable("JAVA_HOME", graalHome)

    val binDir = path.join(graalHome, "bin")
    taskLib.debug(s"Using GraalVM bin directory at $binDir. Contents: ${fs.readdirSync(binDir).join(", ")}")
    taskLib.prependPath(binDir)

    installComponents(binDir, components)
    configurePrefixedExecutables(binDir)
  }

  def run(inputs: Inputs): Unit = {
    import inputs._
    val tool = s"graalvm-ce-java$javaVersion"
    try {
      if (toolLib.findLocalToolVersions(tool).contains(graalVersion.short)) {
        val localPath = toolLib.findLocalTool(tool, graalVersion.short)
        val installedVersion = resolveInstalledVersion(resolveHome(localPath, platform))
        if (installedVersion == graalVersion) {
          taskLib.debug(s"Using locally cached GraalVM $graalVersion for Java $javaVersion from '$localPath'.")
          configureLocalTool(inputs, localPath)
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

  sealed abstract class Arch(val id: String) extends Product with Serializable

  case class Inputs(platform: Platform = taskLib.getPlatform(),
                    arch: Arch = Arch(),
                    graalVersion: GraalVersion = GraalVersion(taskLib.getInput("graalVersion", required = true).get),
                    javaVersion: Int = taskLib.getInput("javaVersion", required = true).get.toInt,
                    nativeImage: Boolean = taskLib.getBoolInput("nativeImage", required = true),
                    llvmToolchain: Boolean = taskLib.getBoolInput("llvmToolchain", required = true),
                    prefixExecutables: Boolean = taskLib.getBoolInput("prefixExecutables", required = true))

  case class GraalVersion(major: Int,
                          minor: Int,
                          patch: Int,
                          update: Option[Int]) {
    override def toString: String = s"$short${if (update.isDefined) s".${update.get}" else ""}"

    def short = s"$major.$minor.$patch"
  }

  object Arch {
    def apply(): Arch = os.arch() match {
      case Arm64.id => Arm64
      case X64.id => X64
      case _ => throw new RuntimeException("""GraalVM CE only supports "x64" (AMD64) or "arm64" (AArch64) runtime architectures at this time.""")
    }

    case object X64 extends Arch("x64")

    case object Arm64 extends Arch("arm64")
  }

  object GraalVersion {

    def apply(graalVersion: String): GraalVersion = {
      val Pattern = """^([1-9]\d*)\.(0|[1-9]\d*)\.(0|[1-9]\d*)(?:\.(0|[1-9]\d*))?$""".r
      Pattern.findFirstMatchIn(graalVersion).flatMap { result =>
        val majorMinorPatch: GraalVersion = GraalVersion(result.group(1).toInt, result.group(2).toInt, result.group(3).toInt, None)
        val update = Option(result.group(4)).map(_.toInt)
        update.map(update => majorMinorPatch.copy(update = Some(update))).orElse(Some(majorMinorPatch))
      }
    }.getOrElse(throw new RuntimeException(s"Unable  to initialise GraalVersion instance from provided setting: $graalVersion"))

  }

  run(Inputs())

}
