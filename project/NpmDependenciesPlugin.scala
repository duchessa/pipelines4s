import org.scalajs.sbtplugin.ScalaJSPlugin
import sbt._
import sbt.Keys._

object NpmDependenciesPlugin extends AutoPlugin {

  override def trigger = allRequirements

  override def requires: Plugins = ScalaJSPlugin

  override def buildSettings: Seq[Setting[_]] = {
    autoImport.parsedNpmDependencies := {
      import _root_.io.circe
      import _root_.io.circe.generic.auto._

      case class PackageJson(dependencies: Map[String, String], devDependencies: Map[String, String])

      val packageJson =
        circe.parser.decode[PackageJson](IO.read((LocalRootProject / baseDirectory).value / "package.json"))
          .getOrElse(throw new RuntimeException("Unable to decode package.json"))

      packageJson.dependencies ++ packageJson.devDependencies
    }

  }

  final case class StringSeqOps(underlying: Seq[String])

  object autoImport {
    val parsedNpmDependencies = settingKey[Map[String, String]]("")

    def fromPackageJson(dependencies: String*): Def.Initialize[Seq[(String, String)]] = Def.setting {
      val parsed = parsedNpmDependencies.value
      dependencies.map(dep => dep -> parsed(dep))
    }
  }


}
