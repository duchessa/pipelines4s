package pipelines4s.graalvm


final case class GraalVersion(major: Int,
                              minor: Int,
                              patch: Int,
                              update: Option[Int]) extends Ordered[GraalVersion] {

  def short = s"$major.$minor.$patch"

  def full = s"$short.${update.getOrElse("0")}"

  override def toString: String = s"$short${if (!update.forall(_ == 0)) s".${update.get}" else ""}"

  override def compare(that: GraalVersion): Int =
    if (major != that.major) major.compare(that.major)
    else if (minor != that.minor) minor.compare(that.minor)
    else if (patch != that.patch) patch.compare(that.patch) else (update, that.update) match {
      case (Some(_), None) => 1
      case (None, Some(_)) => -1
      case (None, None) => 0
      case (Some(a), Some(b)) => a.compare(b)
    }
}


object GraalVersion {

  final val MinimumSupported = apply(21, 0, 0, None)

  def apply(graalVersion: String): GraalVersion = {
    val Pattern = """^([1-9]\d*)\.(0|[1-9]\d*)\.(0|[1-9]\d*)(?:\.(0|[1-9]\d*))?$""".r
    Pattern.findFirstMatchIn(graalVersion).flatMap { result =>
      val majorMinorPatch: GraalVersion = GraalVersion(result.group(1).toInt, result.group(2).toInt, result.group(3).toInt, None)
      val update = Option(result.group(4)).map(_.toInt)
      update.filter(_ != 0).map(update => majorMinorPatch.copy(update = Some(update))).orElse(Some(majorMinorPatch))
    }
  }.getOrElse(throw new RuntimeException(s"Unable  to initialise GraalVersion instance from provided string: $graalVersion"))

}
