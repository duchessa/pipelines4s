addSbtPlugin("org.scala-js" % "sbt-scalajs" % "1.7.0")

addSbtPlugin("ch.epfl.scala" % "sbt-scalajs-bundler" % "0.20.0")

addSbtPlugin("org.scalablytyped.converter" % "sbt-converter" % "1.0.0-beta33")

libraryDependencies ++= Seq(
  "io.circe" %% "circe-parser" % "0.14.1",
  "io.circe" %% "circe-generic" % "0.14.1"
)
