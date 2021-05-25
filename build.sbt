name := "pipelines4s"
version := "0.4.0-SNAPSHOT"
ThisBuild / scalaVersion := "2.13.5"
ThisBuild / crossPaths := false
enablePlugins(ScalaJSPlugin)
aggregateProjects(common, `setup-graalvm`, `setup-sbt`)

lazy val common =
  project
    .enablePlugins(ScalaJSBundlerPlugin, ScalablyTypedConverterPlugin)
    .settings(
      webpackConfigFile := Some((LocalRootProject / baseDirectory).value / "custom.webpack.config.js"),
      scalaJSLinkerConfig ~= (_.withModuleKind(ModuleKind.CommonJSModule)),
      Compile / npmDependencies ++= fromPackageJson("@types/node").value,

    )


lazy val `setup-graalvm` =
  project
    .dependsOn(common)
    .enablePlugins(PipelinesTaskPlugin)
    .settings(
      Compile / npmDependencies ++= fromPackageJson("typed-rest-client").value
    )

lazy val `setup-sbt` =
  project
    .enablePlugins(PipelinesTaskPlugin)

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
