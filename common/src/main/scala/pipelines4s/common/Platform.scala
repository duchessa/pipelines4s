package pipelines4s.common

import typings.node.{osMod => os}
import typings.node.processMod.global.NodeJS

sealed trait Platform {
  def id: String
  def name: String
  override def toString: String = s"$id ($name)"
}

object Platform {

  def apply(): Platform = {
    val p = os.platform()
    if (p == NodeJS.Platform.linux) Linux
    else if (p == NodeJS.Platform.win32) Windows
    else if (p == NodeJS.Platform.darwin) MacOS
    else throw new RuntimeException(s"Unable to resolve Operating System platform. Known platforms: ${known.mkString(", ")}")
  }

  def apply(supported: Platform*): Platform = {
    val resolved = apply()
    if (supported.contains(resolved)) resolved
    else throw new RuntimeException(s"Current Operating System platform not supported. Supported platforms: ${supported.mkString(", ")}")
  }

  def unapply(id: String): Option[Platform] = id match {
    case Linux.id => Some(Linux)
    case Windows.id => Some(Windows)
    case MacOS.id => Some(MacOS)
    case _ => None
  }

  def known = Seq(Linux, Windows, MacOS)

  case object Linux extends Platform {
    final val id = "linux"
    final val name = "Linux"
  }

  case object Windows extends Platform {
    final val id = "win32"
    final val name = "Windows"
  }

  case object MacOS extends Platform {
    final val id = "darwin"
    final val name = "MacOS"
  }

}
