package org.jetbrains.sbt.project.template.wizard

import com.intellij.util.lang.JavaVersion
import org.jetbrains.plugins.scala.project.Version
import org.jetbrains.sbt.SbtVersion

import scala.math.Ordered.orderingToOrdered

object JdkSbtCompatibilityChecker {

  private val JDK_8 = JavaVersion.compose(8)
  /**
   * It's a hardcoded minimum working versions table from [[https://docs.scala-lang.org/overviews/jdk-compatibility/overview.html#tooling-compatibility-table]] <br>
   * In the future, this table should be automatically updated, for example, by a scheduled CI job.
   *
   * The entries in the table represent minimum working versions e.g., the JDK21 requires minimum sbt 1.9.0
   *
   * Example:
   * If the user has JDK 21 and sbt 1.6.2, the warnings displayed will look like this:
   *  - Warning on sbt combo box - sbt 1.9.0+ is recommended with JDK 21
   *  - Warning on JDK combo box - JDK <=20 is recommended with sbt 1.6.2
   */
  private val compatibilityTable: Map[JavaVersion, SbtVersion] = Map(
    JavaVersion.compose(23) -> SbtVersion("1.9.0"),
    JavaVersion.compose(21) -> SbtVersion("1.9.0"),
    JavaVersion.compose(17) -> SbtVersion("1.6.0"),
    JavaVersion.compose(11) -> SbtVersion("1.1.0"),
    JDK_8  -> SbtVersion("1.0.0")
  )

  /**
   * Determines the minimum compatible sbt version required for a specific JDK version.
   * It's done based on hardcoded [[compatibilityTable]].
   *
   * @return [[Option]] containing the minimum required sbt version if the given combination of JDK and sbt is incompatible. <br>
   *         [[None]] if the provided versions are compatible.
   */
  def getMinimumSbtToJdkCompatibleVersion(jdk: JavaVersion, sbtVersion: SbtVersion): Option[SbtVersion] = {
    val nearestCompatibleJdk = compatibilityTable.keys.filter(_ <= jdk).maxOption

    nearestCompatibleJdk
      .map(compatibilityTable)
      .filter(requiredSbtVersion => sbtVersion < requiredSbtVersion)
  }

  /**
   * @param strict if set to <code>true</code>, JDK versions below 1.8 or greater than or equal to 24 are treated as incompatible
   */
  def isSbtAndJdkVersionCompatible(jdk: JavaVersion, sbtVersion: SbtVersion, strict: Boolean = false): Boolean = {
    val isOutsideOfRange = jdk < JDK_8 || jdk >= JavaVersion.compose(24)
    if (strict && isOutsideOfRange) false
    else {
      getMinimumSbtToJdkCompatibleVersion(jdk, sbtVersion).isEmpty
    }
  }

  /**
   * Determines the highest JDK version that is compatible with the given sbt version.
   * It's done based on hardcoded [[compatibilityTable]].
   *
   * For example - for sbt version 1.5.0, this method will return JDK 16, meaning that sbt 1.5.0 is only compatible with JDK <= 16.
   *
   * @return [[Option]] containing the highest compatible JDK for the given sbt version <br>
   *         [[None]] if the versions are compatible
   */
  def getHighestCompatibleJdkForSbt(jdk: JavaVersion, sbtVersion: SbtVersion): Option[JavaVersion] = {
    val isCompatible = isSbtAndJdkVersionCompatible(jdk, sbtVersion)
    if (isCompatible) None
    else {
      val higherSbtVersions = compatibilityTable.filter { case (_, v) => sbtVersion < v }
      val lowestIncompatibleVersion = higherSbtVersions.keys.minOption
      lowestIncompatibleVersion.map(javaVersion => JavaVersion.compose(javaVersion.feature - 1))
    }
  }
}
