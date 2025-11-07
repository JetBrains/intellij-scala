package org.jetbrains.sbt.project.template.wizard

import com.intellij.util.lang.JavaVersion
import org.jetbrains.plugins.scala.ScalaVersion
import org.jetbrains.plugins.scala.project.ScalaLanguageLevel

import scala.math.Ordered.orderingToOrdered

/**
 * Scala/JDK compatibility checker created based on
 * [[https://docs.scala-lang.org/overviews/jdk-compatibility/overview.html#scala-compatibility-table]]
 *
 * It mirrors the logic written in [[JdkSbtCompatibilityChecker]].
 */
object JdkScalaCompatibilityChecker:
  private val JDK_8 = JavaVersion.compose(8)

  /**
   * Hardcoded minimum Scala versions per JDK.
   *
   * Fields in this class represent columns in the Scala compatibility table.
   * As of October 30, 2025, these are: 3, 3 LTS (3.3.x), 2.13, 2.12, and 2.11.
   * A `None` value in any field indicates no known support for the given JDK.
   */
  private case class ScalaMinimumsPerJdk(
    scala3: Option[ScalaVersion],
    scala3Lts: Option[ScalaVersion],
    scala213: Option[ScalaVersion],
    scala212: Option[ScalaVersion],
    scala211: Option[ScalaVersion]
  )
  // 2025-10-30 based on https://docs.scala-lang.org/overviews/jdk-compatibility/overview.html#scala-compatibility-table
  // 25 (LTS) | 3.7.1 | 3.3.6 | 2.13.17 | 2.12.21* |
  // 21 (LTS) | 3.4.0 | 3.3.1 | 2.13.11 | 2.12.18  |
  // 17 (LTS) | 3.0.0 | 3.3.0 | 2.13.6  | 2.12.15  |
  // 11 (LTS) | 3.0.0 | 3.3.0 | 2.13.0  | 2.12.4   | 2.11.12
  // 8  (LTS) | 3.0.0 | 3.3.0 | 2.13.0  | 2.12.0   | 2.11.0
  private val compatibilityTable: Map[JavaVersion, ScalaMinimumsPerJdk] = Map(
    JavaVersion.compose(25) -> ScalaMinimumsPerJdk(
      scala3    = Some(ScalaVersion(ScalaLanguageLevel.Scala_3_7, "1")),
      scala3Lts = Some(ScalaVersion(ScalaLanguageLevel.Scala_3_3, "6")),
      scala213  = Some(ScalaVersion(ScalaLanguageLevel.Scala_2_13, "17")),
      scala212  = Some(ScalaVersion(ScalaLanguageLevel.Scala_2_12, "21")), // forthcoming in docs
      scala211  = None
    ),
    JavaVersion.compose(21) -> ScalaMinimumsPerJdk(
      scala3    = Some(ScalaVersion(ScalaLanguageLevel.Scala_3_4, "0")),
      scala3Lts = Some(ScalaVersion(ScalaLanguageLevel.Scala_3_3, "1")),
      scala213  = Some(ScalaVersion(ScalaLanguageLevel.Scala_2_13, "11")),
      scala212  = Some(ScalaVersion(ScalaLanguageLevel.Scala_2_12, "18")),
      scala211  = None
    ),
    JavaVersion.compose(17) -> ScalaMinimumsPerJdk(
      scala3    = Some(ScalaVersion(ScalaLanguageLevel.Scala_3_0, "0")),
      scala3Lts = Some(ScalaVersion(ScalaLanguageLevel.Scala_3_3, "0")),
      scala213  = Some(ScalaVersion(ScalaLanguageLevel.Scala_2_13, "6")),
      scala212  = Some(ScalaVersion(ScalaLanguageLevel.Scala_2_12, "15")),
      scala211  = None
    ),
    JavaVersion.compose(11) -> ScalaMinimumsPerJdk(
      scala3    = Some(ScalaVersion(ScalaLanguageLevel.Scala_3_0, "0")),
      scala3Lts = Some(ScalaVersion(ScalaLanguageLevel.Scala_3_3, "0")),
      scala213  = Some(ScalaVersion(ScalaLanguageLevel.Scala_2_13, "0")),
      scala212  = Some(ScalaVersion(ScalaLanguageLevel.Scala_2_12, "4")),
      scala211  = Some(ScalaVersion(ScalaLanguageLevel.Scala_2_11, "12"))
    ),
    JDK_8 -> ScalaMinimumsPerJdk(
      scala3    = Some(ScalaVersion(ScalaLanguageLevel.Scala_3_0, "0")),
      scala3Lts = Some(ScalaVersion(ScalaLanguageLevel.Scala_3_3, "0")),
      scala213  = Some(ScalaVersion(ScalaLanguageLevel.Scala_2_13, "0")),
      scala212  = Some(ScalaVersion(ScalaLanguageLevel.Scala_2_12, "0")),
      scala211  = Some(ScalaVersion(ScalaLanguageLevel.Scala_2_11, "0"))
    )
  )

  private def isScala3Lts(v: ScalaVersion): Boolean = v.languageLevel.getVersion.startsWith("3.3")
  private def isScala3_8Plus(v: ScalaVersion): Boolean = v.languageLevel >= ScalaLanguageLevel.Scala_3_8
  private def isScala2_13(v: ScalaVersion): Boolean = v.languageLevel.getVersion.startsWith("2.13")
  private def isScala2_12(v: ScalaVersion): Boolean = v.languageLevel.getVersion.startsWith("2.12")
  private def isScala2_11(v: ScalaVersion): Boolean = v.languageLevel.getVersion.startsWith("2.11")

  private def minimumRequiredScala(scalaMinVersions: ScalaMinimumsPerJdk, scalaVersion: ScalaVersion): Option[ScalaVersion] =
    if (isScala3Lts(scalaVersion)) scalaMinVersions.scala3Lts
    else if (scalaVersion.isScala3) scalaMinVersions.scala3
    else if (isScala2_13(scalaVersion)) scalaMinVersions.scala213
    else if (isScala2_12(scalaVersion)) scalaMinVersions.scala212
    else if (isScala2_11(scalaVersion)) scalaMinVersions.scala211
    else None

  /**
   * Determines the minimum Scala version required to be compatible with the specified JDK.
   *
   * '''Examples:'''
   *  - JDK 17 + Scala 2.11.10: Returns Scala 2.12.15 because Scala 2.11.10 doesn't support JDK 17.
   *
   *  - JDK 16 + Scala 2.12.10: Returns `None`(compatible). The nearest JDK below or equal to 16 is 11,
   *    which requires Scala 2.12.4+, so we assume JDK 16 + Scala 2.12.10 are compatible (though there is no explicit information about this).
   *
   *  - JDK 21 + Scala 2.12.20: Returns `None` (compatible) because JDK 21 requires Scala 2.12.18+.
   *
   * @return [[Option]] containing the minimum required Scala version if the given combination of JDK and Scala is incompatible. <br>
   *         [[None]] if the provided versions are compatible.
   */
  def getMinimumScalaToJdkCompatibleVersion(jdk: JavaVersion, scalaVersion: ScalaVersion): Option[ScalaVersion] = {
    val nearestJdkBelowOrEqual = compatibilityTable.keys.filter(_ <= jdk).maxOption
    nearestJdkBelowOrEqual.flatMap { javaVersion =>
      val scalaMinsPerJDK = compatibilityTable(javaVersion)
      val minVersion = minimumRequiredScala(scalaMinsPerJDK, scalaVersion)
      minVersion match {
        case Some(required) if scalaVersion < required => Some(required)
        case _ => None // versions are compatible or there is no information
      }
    }
  }

  private def isScalaAndJdkVersionCompatible(jdk: JavaVersion, scalaVersion: ScalaVersion): Boolean =
    getMinimumJdkRequiredForScala(jdk, scalaVersion).isEmpty &&
      getMinimumScalaToJdkCompatibleVersion(jdk, scalaVersion).isEmpty

  /**
   * Returns the minimum required JDK version if the current JDK is incompatible with the given Scala version.
   *
   * Currently, it handles the special case where Scala 3.8+ requires JDK 17 or higher.
   * See: [[https://www.scala-lang.org/news/next-scala-lts-jdk.html]]
   *
   * @return the minimum JDK version required for compatibility, or `None` if the current JDK is enough.
   */
  def getMinimumJdkRequiredForScala(jdk: JavaVersion, scalaVersion: ScalaVersion): Option[JavaVersion] =
    val required = if (isScala3_8Plus(scalaVersion)) Some(JavaVersion.compose(17)) else None
    required.filter(jdk < _)

  /**
   * Determines the highest JDK version that is compatible with the given Scala version.
   * Mirrors the logic from [[JdkSbtCompatibilityChecker.getHighestCompatibleJdkForSbt]].
   *
   * '''Examples:'''
   *  - JDK 21 + Scala 2.12.10: Returns JDK 16. Scala 2.12.10 is incompatible with JDK 21, and the highest compatible JDK
   *    for Scala 2.12.10 is JDK 16. JDK 17 requires Scala 2.12.15+.
   *
   *  - JDK 21 + Scala 3.3.0: Returns JDK 20. Scala 3.3.1 is the first version that supports JDK 21, so the highest compatible JDK for Scala 3.3.0 is JDK 20.
   *
   *  - JDK 21 + Scala 2.13.15: Returns `None` (compatible) because JDK 21 requires Scala 2.13.11+ and version 2.13.15 meets this requirement.
   *
   * @return [[Option]] containing the highest compatible JDK for the given Scala version <br>
   *         [[None]] if the versions are compatible
   */
  def getHighestCompatibleJdkForScala(jdk: JavaVersion, scalaVersion: ScalaVersion): Option[JavaVersion] = {
    val isCompatible = isScalaAndJdkVersionCompatible(jdk, scalaVersion)
    if (isCompatible) None
    else {
      val lowestIncompatibleJdk = compatibilityTable.keys
        .toSeq
        .sorted
        .find { javaVersion =>
          val scalaMinimumsPerJdk = compatibilityTable(javaVersion)
          minimumRequiredScala(scalaMinimumsPerJdk, scalaVersion) match {
            case Some(min) => scalaVersion < min
            case None => true
          }
        }

      lowestIncompatibleJdk.map(j => JavaVersion.compose(j.feature - 1))
    }
  }
