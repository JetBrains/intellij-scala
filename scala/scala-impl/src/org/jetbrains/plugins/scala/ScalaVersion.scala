package org.jetbrains.plugins.scala

import com.intellij.lang.Language
import org.jetbrains.plugins.scala.project.ScalaLanguageLevel
import org.jetbrains.plugins.scala.util.HashBuilder._
import org.jetbrains.sbt.MinorVersionGenerator

final class ScalaVersion(
  val languageLevel: ScalaLanguageLevel,
  val minorSuffix: String
) extends Ordered[ScalaVersion] with MinorVersionGenerator[ScalaVersion] {

  /**
   * @return a major scala version.<br>
   *         Examples: 2.12, 2.13, 3.3, 3.5
   */
  def major: String = languageLevel.getVersion

  /**
   * @return full scala version<br>
   *         Examples: 2.12.15, 3.3.3, 3.5.2-RC1
   */
  def minor: String = major + "." + minorSuffix

  @inline def isScala2: Boolean = languageLevel.isScala2
  @inline def isScala3: Boolean = languageLevel.isScala3
  @inline def language: Language = languageLevel.getLanguage

  lazy val minorVersion: project.Version = project.Version(minorSuffix)

  override def compare(that: ScalaVersion): Int =
    (languageLevel, minorVersion) compare (that.languageLevel, that.minorVersion)

  def withMinor(newMinorSuffix: Int): ScalaVersion = withMinor(newMinorSuffix.toString)
  def withMinor(newMinorSuffix: String): ScalaVersion = new ScalaVersion(languageLevel, newMinorSuffix)

  override def equals(other: Any): Boolean = other match {
    case that: ScalaVersion =>
      that.languageLevel == languageLevel &&
        that.minorSuffix == minorSuffix
    case _ => false
  }

  override def hashCode(): Int = languageLevel #+ minorSuffix

  override def toString: String = s"ScalaVersion($minor)"

  override def generateNewVersion(version: String): Option[ScalaVersion] = ScalaVersion.fromString(version)
}

//noinspection TypeAnnotation
object ScalaVersion {

  def Latest: LatestScalaVersions.type = LatestScalaVersions

  def default: ScalaVersion =
    LatestScalaVersions.allStableWithoutScalaNext.find(_.languageLevel == ScalaLanguageLevel.getDefault).get

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

/**
 * [NOTE] If you need to update the latest supported Scala_3_X version:
 *  - update sbt bridge in `runtimeDependencies` of `community/build.sbt`
 *  - create new ScalaLanguageLevel.Scala_3_x (if new major scala 3 version was released)
 *  - update all latest minor versions LatestScalaVersions.Scala_3_X to the latest
 *  - when adding new version also update org.jetbrains.plugins.scala.util.runners.TestScalaVersion
 *  - run `AfterUpdateDottyVersionScript.scala`
 */
object LatestScalaVersions {

  //
  // Exact versions
  //
  val Scala_2_9  = new ScalaVersion(ScalaLanguageLevel.Scala_2_9, "3")
  val Scala_2_10 = new ScalaVersion(ScalaLanguageLevel.Scala_2_10, "7")
  val Scala_2_11 = new ScalaVersion(ScalaLanguageLevel.Scala_2_11, "12")
  val Scala_2_12 = new ScalaVersion(ScalaLanguageLevel.Scala_2_12, "20")
  val Scala_2_13 = new ScalaVersion(ScalaLanguageLevel.Scala_2_13, "15")
  val Scala_3_0  = new ScalaVersion(ScalaLanguageLevel.Scala_3_0, "2")
  val Scala_3_1  = new ScalaVersion(ScalaLanguageLevel.Scala_3_1, "3")
  val Scala_3_2  = new ScalaVersion(ScalaLanguageLevel.Scala_3_2, "2")
  val Scala_3_3 = new ScalaVersion(ScalaLanguageLevel.Scala_3_3, "4")
  val Scala_3_4 = new ScalaVersion(ScalaLanguageLevel.Scala_3_4, "2")
  val Scala_3_5 = new ScalaVersion(ScalaLanguageLevel.Scala_3_5, "1")

  //
  // Aliases
  //
  val Scala_2: ScalaVersion = Scala_2_13
  val Scala_3_LTS: ScalaVersion = Scala_3_3 // Scala 3.3 is an LTS version
  val Scala_3: ScalaVersion = Scala_3_LTS

  //
  // Release candidates
  //
  // Scala LTS RC
  val Scala_3_LTS_RC: ScalaVersion = Scala_3_3.withMinor("4-RC4")

  // Scala Next RC
  val Scala_3_Next_RC = new ScalaVersion(ScalaLanguageLevel.Scala_3_5, "2-RC2")

  val allScala2: Seq[ScalaVersion] = Seq(
    Scala_2_9,
    Scala_2_10,
    Scala_2_11,
    Scala_2_12,
    Scala_2_13
  )

  val allScala3: Seq[ScalaVersion] = Seq(
    Scala_3_0,
    Scala_3_1,
    Scala_3_2,
    Scala_3_3
  )

  val allStableWithoutScalaNext: Seq[ScalaVersion] =
    allScala2 ++ allScala3

  val allScalaNext: Seq[ScalaVersion] = Seq(
    Scala_3_4,
    Scala_3_5,
  )

  val allReleaseCandidates: Seq[ScalaVersion] = Seq(
    Scala_3_LTS_RC,
    Scala_3_Next_RC,
  )
}
