package org.jetbrains.plugins.scala

import org.jetbrains.plugins.scala.project.ScalaLanguageLevel
import org.jetbrains.plugins.scala.util.HashBuilder._

class ScalaVersion(
  val languageLevel: ScalaLanguageLevel,
  val minorSuffix: String
) extends Ordered[ScalaVersion] {

  def major: String = languageLevel.getVersion

  def minor: String = major + "." + minorSuffix

  override def compare(that: ScalaVersion): Int =
    (languageLevel, project.Version(minor)) compare (that.languageLevel, project.Version(that.minor))

  def withMinor(newMinorSuffix: String): ScalaVersion = new ScalaVersion(languageLevel, newMinorSuffix)
  def withMinor(newMinorSuffix: Int): ScalaVersion = withMinor(newMinorSuffix.toString)

  override def equals(other: Any): Boolean = other match {
    case that: ScalaVersion =>
      that.languageLevel == languageLevel &&
        that.minorSuffix == minorSuffix
    case _ => false
  }

  override def hashCode(): Int = languageLevel #+ minorSuffix

  override def toString: String = s"ScalaVersion($minor)"
}

//noinspection TypeAnnotation
object ScalaVersion {

  def Latest = LatestScalaVersions

  def default: ScalaVersion =
    LatestScalaVersions.all.find(_.languageLevel == ScalaLanguageLevel.getDefault).get

  private val versionRegex = """(\d)\.(\d\d?)\.(.+)""".r

  /**
   * @param versionString minor scala version in format 2.13.2
   */
  def fromString(versionString: String): Option[ScalaVersion] =
    versionString match {
      case versionRegex(_, _, z) =>
        val level = ScalaLanguageLevel.findByVersion(versionString)
        level.map(new ScalaVersion(_, z))
      case _                     => None
    }
}

object LatestScalaVersions {

  // duplicated here to refer with `ScalaVersion.` prefix
  val Scala_2_9  = new ScalaVersion(ScalaLanguageLevel.Scala_2_9, "3")
  val Scala_2_10 = new ScalaVersion(ScalaLanguageLevel.Scala_2_10, "7")
  val Scala_2_11 = new ScalaVersion(ScalaLanguageLevel.Scala_2_11, "12")
  val Scala_2_12 = new ScalaVersion(ScalaLanguageLevel.Scala_2_12, "10")
  val Scala_2_13 = new ScalaVersion(ScalaLanguageLevel.Scala_2_13, "1")
  val Scala_3_0 = new ScalaVersion(ScalaLanguageLevel.Scala_3_0, "0-RC2")
  final val Dotty = Scala_3_0
  
  val all: Seq[ScalaVersion] = Seq(
    Scala_2_9,
    Scala_2_10,
    Scala_2_11,
    Scala_2_12,
    Scala_2_13,
    Scala_3_0
  )
}
