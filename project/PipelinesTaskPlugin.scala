import _root_.io.circe
import _root_.io.circe.generic.auto._
import _root_.io.circe.syntax._
import org.scalablytyped.converter.plugin.ScalablyTypedConverterPlugin
import org.scalablytyped.converter.plugin.ScalablyTypedPluginBase.autoImport._
import org.scalajs.sbtplugin.ScalaJSPlugin.autoImport._
import sbt._
import sbt.Keys._
import scalajsbundler.sbtplugin.ScalaJSBundlerPlugin
import scalajsbundler.sbtplugin.ScalaJSBundlerPlugin.autoImport._
import NpmDependenciesPlugin.autoImport.fromPackageJson

import java.nio.charset.Charset


object PipelinesTaskPlugin extends AutoPlugin {

  override def trigger = noTrigger

  import autoImport._

  override def projectConfigurations: Seq[Configuration] = Seq(PipelinesTask)

  override def requires = ScalaJSBundlerPlugin && ScalablyTypedConverterPlugin

  override def projectSettings: Seq[Setting[_]] = {
    Seq(
      scalaVersion := (LocalRootProject / scalaVersion).value,
      version := (LocalRootProject / version).value,
      scalaJSLinkerConfig ~= (_.withModuleKind(ModuleKind.CommonJSModule)),
      webpackBundlingMode := BundlingMode.Application,
      webpackConfigFile := Some((LocalRootProject / baseDirectory).value / "custom.webpack.config.js"),
      stUseScalaJsDom := false,
      stFlavour := org.scalablytyped.converter.Flavour.Normal,
      PipelinesTask / target := target.value / PipelinesTask.name,
      PipelinesTask / Keys.`package` := {
        val nodeModules = (Compile / npmUpdate).value / "node_modules"
        val targetDir = (PipelinesTask / target).value
        val bundled = (Compile / fastOptJS / webpack).value
        val staticResources = (Compile / unmanagedResources).value

        def processResources(): Unit = {

          case class LocLib(messages: Map[String, String])

          for {
            taskLibLoc <- circe.parser.decode[LocLib](IO.read(nodeModules / "azure-pipelines-task-lib" / "lib.json"))
            toolLibLoc <- circe.parser.decode[LocLib](IO.read(nodeModules / "azure-pipelines-tool-lib" / "lib.json"))
          } yield IO.write(targetDir / "lib.json", LocLib(taskLibLoc.messages ++ toolLibLoc.messages).asJson.spaces2, Charset.defaultCharset(), append = false)

          staticResources.foreach(f => if (f.isFile) IO.copyFile(f, targetDir / f.name))
        }

        def processOutput: File = {
          val mappings = bundled.map(f => f.data -> targetDir / f.data.name)
          mappings.foreach { pair =>
            if (pair._2.name.endsWith("bundle.js")) IO.write(
              pair._2,
              IO.read(pair._1)
                .replaceAll("""(__webpack_require__\(.*\)\(resourceFile\))""", "_loadResJson(resourceFile)")
                .replaceAll("""(let pkg.*'package.json'\)\))""", "let pkg = { version: \"1.0.0\" };")
            ) else IO.copyFile(pair._1, pair._2)
          }
          targetDir
        }

        if (targetDir.exists) {
          IO.delete(targetDir)
          IO.createDirectory(targetDir)
        } else IO.createDirectory(targetDir)
        processResources()
        processOutput


      },
      Compile / npmDependencies ++= fromPackageJson(
        "azure-pipelines-task-lib",
        "azure-pipelines-tool-lib"
      ).value,
      Compile / npmDevDependencies ++= fromPackageJson(
        "@types/node",
        "@types/q",
        "@types/shelljs",
        "webpack-merge"
      ).value
    )
  }

  object autoImport {
    val PipelinesTask = Configuration.of("PipelinesTask", "pipelines-task")
  }

}
