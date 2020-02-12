package org.jetbrains.plugins.scala

import org.jetbrains.plugins.scala.project.ScalaLanguageLevel
import org.jetbrains.plugins.scala.util.HashBuilder._

/**
 * @author Nikolay.Tropin
 */
class ScalaVersion protected(val languageLevel: ScalaLanguageLevel,
                             val minorSuffix: String) extends Ordered[ScalaVersion] {

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
  import org.jetbrains.plugins.scala

  // duplicated here to refer with `ScalaVersion.` prefix
  val Scala_2_9  = scala.Scala_2_9
  val Scala_2_10 = scala.Scala_2_10
  val Scala_2_11 = scala.Scala_2_11
  val Scala_2_12 = scala.Scala_2_12
  val Scala_2_13 = scala.Scala_2_13
  val Scala_3_0 = scala.Scala_3_0

  val allScalaVersions: Seq[ScalaVersion] = Seq(
    Scala_2_9,
    Scala_2_10,
    Scala_2_11,
    Scala_2_12,
    Scala_2_13,
    Scala_3_0
  )

  def default: ScalaVersion =
    allScalaVersions.find(_.languageLevel == ScalaLanguageLevel.getDefault).get

  def fromString(versionString: String): Option[ScalaVersion] =
    ScalaVersion.allScalaVersions.find(_.toString == versionString)
}

// TODO: make them `val`s and move all these versions to ScalaVersion companion object
object Scala_2_9 extends ScalaVersion(ScalaLanguageLevel.Scala_2_9, "3")
object Scala_2_10 extends ScalaVersion(ScalaLanguageLevel.Scala_2_10, "7")
object Scala_2_11 extends ScalaVersion(ScalaLanguageLevel.Scala_2_11, "12")
object Scala_2_12 extends ScalaVersion(ScalaLanguageLevel.Scala_2_12, "10")
object Scala_2_13 extends ScalaVersion(ScalaLanguageLevel.Scala_2_13, "1")
object Scala_3_0 extends ScalaVersion(ScalaLanguageLevel.Scala_3_0, "0-RC1")