package org.jetbrains.plugins.scala

import org.jetbrains.plugins.scala.project.ScalaLanguageLevel

/**
 * @author Nikolay.Tropin
 */
class ScalaVersion protected(val languageLevel: ScalaLanguageLevel,
                             val minorSuffix: String) extends Ordered[ScalaVersion] {

  def major: String = languageLevel.getVersion

  def minor: String = major + "." + minorSuffix

  override def compare(that: ScalaVersion): Int =
    ScalaVersion.scalaVersionOrdering.compare(this, that)

  def withMinor(newMinorSuffix: String): ScalaVersion = new ScalaVersion(languageLevel, newMinorSuffix)
  def withMinor(newMinorSuffix: Int): ScalaVersion = withMinor(newMinorSuffix.toString)

  override def equals(other: Any): Boolean = other match {
    case that: ScalaVersion =>
      that.languageLevel == languageLevel &&
        that.minorSuffix == minorSuffix
    case _ => false
  }

  override def hashCode(): Int = {
    val state = Seq(languageLevel, minorSuffix)
    state.map(_.hashCode()).foldLeft(0)((a, b) => 31 * a + b)
  }

  override def toString: String = s"ScalaVersion($languageLevel, $minor)"
}

object ScalaVersion {

  implicit val scalaVersionOrdering: Ordering[ScalaVersion] =
    Ordering[(ScalaLanguageLevel, Int, String)].on[ScalaVersion] { v =>
      (v.languageLevel, v.minorSuffix.length, v.minorSuffix)
    }

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