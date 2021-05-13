import typings.azurePipelinesTaskLib.{mod => taskLib}
import typings.azurePipelinesTaskLib.mod.Platform
import typings.azurePipelinesToolLib.{toolMod => toolLib}
import typings.node.{fsMod => fs, pathMod => path}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.scalajs.js.annotation.JSExportTopLevel

@JSExportTopLevel("SetupSbt", "setup-sbt")
object SetupSbt {

  def sbtBinDir(toolPath: String): String = path.join(toolPath, "bin")

  def run(sbtVersionInput: String): Unit = try {
    if (toolLib.findLocalToolVersions("sbt").contains(sbtVersionInput)) {
      val toolPath = toolLib.findLocalTool("sbt", sbtVersionInput)
      taskLib.debug(s"Using locally cached sbt $sbtVersionInput from $toolPath.")
      val binDir = sbtBinDir(toolPath)
      taskLib.debug(s"Resolved existing sbt bin directory at $binDir. Contents: ${fs.readdirSync(binDir).join(", ")}")
      taskLib.prependPath(binDir)
    } else {
      val windows = taskLib.getPlatform() == Platform.Windows
      def extension = if (windows) "zip" else "tgz"
      val downloadUrl = s"https://github.com/sbt/sbt/releases/download/v${sbtVersionInput}/sbt-$sbtVersionInput.$extension"

      taskLib.debug(s"Downloading sbt launcher from: $downloadUrl")
      val downloaded = toolLib.downloadTool(downloadUrl).toFuture

      val extracted =
        downloaded.flatMap { file => if (windows) toolLib.extractZip(file).toFuture else toolLib.extractTar(file).toFuture }

      extracted.foreach(str => taskLib.debug(s"Extracted sbt launcher to: $str."))

      val toolPath = extracted.map(dir => path.join(dir, "sbt"))

      val cached = toolPath.flatMap(dir => toolLib.cacheDir(dir, "sbt", sbtVersionInput).toFuture)

      cached.foreach { dir =>
        taskLib.debug(s"Cached sbt launcher $sbtVersionInput locally at $cached. Contents: ${fs.readdirSync(dir).join(", ")}")
        run(sbtVersionInput)
      }
    }
  } catch {
    case err: Throwable => throw new RuntimeException("Unable to execute setup-sbt task.", err)
  }

  run(taskLib.getInput("sbtVersion", required = true).get)
}
