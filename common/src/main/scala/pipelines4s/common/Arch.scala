package pipelines4s.common

import typings.node.{osMod => os}

sealed trait Arch {
  def id: String
  def name: String
  override def toString: String = s"$id ($name)"
}

object Arch {

  def apply(): Arch = os.arch() match {
    case Arch(arch) => arch
    case _ => throw new RuntimeException(s"pipelines4s only supports the following processor architectures at this time: ${supported.mkString(", ")}")
  }

  def unapply(id: String): Option[Arch] = id match {
    case X64.id => Some(X64)
    case X32.id => Some(X32)
    case Arm64.id => Some(Arm64)
    case _ => None
  }

  def supported = List(X32, X64, Arm64)

  case object X32 extends Arch {
    final val name = "x86"
    final val id = "x32"
  }

  case object X64 extends Arch {
    final val name = "AMD64"
    final val id = "x64"
  }

  case object Arm64 extends Arch {
    final val name = "AArch64"
    final val id = "arm64"
  }

}
