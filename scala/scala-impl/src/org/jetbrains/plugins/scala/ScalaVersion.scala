package org.jetbrains.plugins.scala

import org.jetbrains.plugins.scala.project.ScalaLanguageLevel

/**
 * @author Nikolay.Tropin
 */
sealed abstract class ScalaVersion(languageLevel: ScalaLanguageLevel,
                                   minorSuffix: String) {
  def major: String = languageVersion

  def minor: String = s"$languageVersion.$minorSuffix"

  private def languageVersion = languageLevel.getVersion
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
  "3"
)

case object Scala_2_13 extends ScalaVersion(
  ScalaLanguageLevel.Scala_2_13,
  "0-RC3"
) {
  override def major: String = minor
}

case object Scala_3_0 extends ScalaVersion(
  ScalaLanguageLevel.Scala_3_0,
  "0-RC1"
) {
  override def major: String = minor
}