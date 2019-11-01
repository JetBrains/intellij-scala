package org.jetbrains.plugins.scala

import org.jetbrains.plugins.scala.project.ScalaLanguageLevel

/**
 * @author Nikolay.Tropin
 */
sealed abstract class ScalaVersion(val languageLevel: ScalaLanguageLevel,
                                   private val minorSuffix: String) extends Ordered[ScalaVersion] {

  def major: String = languageLevel.getVersion

  def minor: String = major + "." + minorSuffix

  override def compare(that: ScalaVersion): Int = languageLevel.compare(that.languageLevel)
}

object ScalaVersion {
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

case object Scala_2_9 extends ScalaVersion(
  ScalaLanguageLevel.Scala_2_9,
  "3"
)

case object Scala_2_10 extends ScalaVersion(
  ScalaLanguageLevel.Scala_2_10,
  "7"
)

case object Scala_2_11 extends ScalaVersion(
  ScalaLanguageLevel.Scala_2_11,
  "12"
)

case object Scala_2_12 extends ScalaVersion(
  ScalaLanguageLevel.Scala_2_12,
  "10"
)

case object Scala_2_13 extends ScalaVersion(
  ScalaLanguageLevel.Scala_2_13,
  "1"
)

case object Scala_3_0 extends ScalaVersion(
  ScalaLanguageLevel.Scala_3_0,
  "0-RC1"
)