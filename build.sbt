name := "pipelines4s"
version := "0.5.1-SNAPSHOT"
ThisBuild / scalaVersion := "2.13.5"
ThisBuild / crossPaths := false
enablePlugins(ScalaJSPlugin)
aggregateProjects(common, `setup-graalvm`, `setup-sbt`)

Global / stQuiet := true

lazy val common =
  project
    .enablePlugins(ScalaJSBundlerPlugin, ScalablyTypedConverterPlugin)
    .settings(sharedSettings)
    .settings(
      Compile / npmDependencies ++= fromPackageJson("@types/node").value,
    )


lazy val `setup-graalvm` =
  project
    .dependsOn(common)
    .enablePlugins(PipelinesTaskPlugin)
    .settings(sharedSettings)
    .settings(
      Compile / npmDependencies ++= fromPackageJson("typed-rest-client").value
    )

lazy val `setup-sbt` =
  project
    .enablePlugins(PipelinesTaskPlugin)
    .settings(sharedSettings)

Keys.`package` := {
  val rootDir = (LocalRootProject / baseDirectory).value
  val setupGraalvmTask = (`setup-graalvm` / PipelinesTask / Keys.`package`).value -> `setup-graalvm`
  val setupSbtTask = (`setup-sbt` / PipelinesTask / Keys.`package`).value -> `setup-sbt`
  val targetDir = target.value / "vsix"
  val tasksTarget = targetDir / "tasks"

  def copyTasks(pairs: (File, Project)*): Unit = {
    pairs.foreach((pair: (sbt.File, Project)) => IO.copyDirectory(pair._1, tasksTarget / pair._2.id))
  }

  copyTasks(
    setupGraalvmTask,
    setupSbtTask
  )

  (IO.listFiles(rootDir / "vss-extension") ++ Seq(
    rootDir / "README.md",
    rootDir / "LICENSE",
    rootDir / "NOTICE"
  )).foreach { file =>
    if (file.isDirectory) IO.copyDirectory(file, targetDir / file.name)
    else IO.copyFile(file, targetDir / file.name)
  }
  targetDir
}

def sharedSettings = Seq(
  scalaVersion := (LocalRootProject / scalaVersion).value,
  version := (LocalRootProject / version).value,
  crossPaths := (LocalRootProject / crossPaths).value,
  scalaJSLinkerConfig ~= (_.withModuleKind(ModuleKind.CommonJSModule)),
  webpackBundlingMode := BundlingMode.Application,
  webpackConfigFile := Some((LocalRootProject / baseDirectory).value / "custom.webpack.config.js"),
  stFlavour := org.scalablytyped.converter.Flavour.Normal,
  stTypescriptVersion := fromPackageJson("typescript").value.head._2,
  stEnableLongApplyMethod := true,
  stEnableScalaJsDefined := Selection.All
)
