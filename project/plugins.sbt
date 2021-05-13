addSbtPlugin("org.scala-js" % "sbt-scalajs" % "1.5.1")

addSbtPlugin("ch.epfl.scala" % "sbt-scalajs-bundler" % "0.20.0")

addSbtPlugin("org.scalablytyped.converter" % "sbt-converter" % "1.0.0-beta32")

libraryDependencies ++= Seq(
  "io.circe" %% "circe-parser" % "0.14.0-M6",
  "io.circe" %% "circe-generic" % "0.14.0-M6"
)
