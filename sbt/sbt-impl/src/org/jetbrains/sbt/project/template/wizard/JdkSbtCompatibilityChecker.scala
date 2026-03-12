package org.jetbrains.sbt.project.template.wizard

import com.intellij.util.lang.JavaVersion
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
    JavaVersion.compose(25) -> SbtVersion("1.9.0"),
    JavaVersion.compose(21) -> SbtVersion("1.9.0"),
    JavaVersion.compose(17) -> SbtVersion("1.6.0"),
    JavaVersion.compose(11) -> SbtVersion("1.1.0"),
    JDK_8  -> SbtVersion("1.0.0")
  )

  /** Maps sbt versions to their minimum required JDK version */
  private val sbtToMinJdkVersionTable: Map[SbtVersion, JavaVersion] = Map(
    SbtVersion("2.0.0-RC9") -> JavaVersion.compose(17)
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
   * Returns the minimum JDK version required for the given sbt version, or `None` if there is no minimum JDK constraint.
   *
   * @see [[org.jetbrains.sbt.project.template.wizard.JdkScalaCompatibilityChecker.getMinimumJdkVersionForScala]]
   */
  def getMinimumJdkVersionForSbt(sbtVersion: SbtVersion): Option[JavaVersion] = {
    val nearestCompatibleSbt = sbtToMinJdkVersionTable.keys.filter(_ <= sbtVersion).maxOption
    nearestCompatibleSbt.map(sbtToMinJdkVersionTable)
  }

  /**
   * Returns `None` if the `jdk` is compatible with the given `sbtVersion`, or the minimum required JDK version otherwise.
   *
   * @see [[getMinimumJdkVersionForSbt]]
   * @todo (IMPORTANT) Currently, when checking the minimum required JDK for sbt version, the upper bound does not need to be checked,
   *       because sbt 2.0.0-RC9 supports all available JDKs. However, this will likely change in the future.
   *       For example, if JDK 27 is introduced and supported only in sbt 2.1, then when the user has sbt 2.0.0 & JDK 11 in the NPW,
   *       the warning "JDK >= 17 is required for sbt 2.0.0" would be incomplete - it should also include an upper bound,
   *       e.g. "JDK >= 17 and < 27 is required for sbt 2.0.0" (or sth similar, it's just an example).
   */
  def getMinimumJdkToSbtCompatibleVersion(jdk: JavaVersion, sbtVersion: SbtVersion): Option[JavaVersion] =
    getMinimumJdkVersionForSbt(sbtVersion).filter(jdk < _)

  /**
   * @param strict if set to `true`, JDK versions below 1.8 or greater than 25 are treated as incompatible
   */
  def isSbtAndJdkVersionCompatible(jdk: JavaVersion, sbtVersion: SbtVersion, strict: Boolean = false): Boolean = {
    val isOutsideOfRange = jdk < JDK_8 || jdk > JavaVersion.compose(25)
    if (strict && isOutsideOfRange) false
    else {
      getMinimumSbtToJdkCompatibleVersion(jdk, sbtVersion).isEmpty &&
        getMinimumJdkToSbtCompatibleVersion(jdk, sbtVersion).isEmpty
    }
  }

  /**
   * Returns the highest JDK version that is compatible with the given sbt version.
   * It's done based on hardcoded [[compatibilityTable]].
   *
   * For example - for sbt version 1.5.0, this method will return JDK 16, meaning that sbt 1.5.0 is only compatible with JDK <= 16.
   *
   * @return the highest compatible JDK for the given sbt version, or `None` if there is no requirement for the given sbt version.
   */
  def getHighestCompatibleJdkForSbt(sbtVersion: SbtVersion): Option[JavaVersion] = {
    val higherSbtVersions = compatibilityTable.filter { case (_, v) => sbtVersion < v }
    val lowestIncompatibleVersion = higherSbtVersions.keys.minOption
    lowestIncompatibleVersion.map(javaVersion => JavaVersion.compose(javaVersion.feature - 1))
  }

  /**
   * Returns `None` if the `jdk` is compatible with the given `sbtVersion`, or the highest compatible JDK otherwise.
   *
   * @note In some UI checks, before verifying the highest compatible version, we should first check the minimum JDK compatibility.
   *       [[getMinimumJdkToSbtCompatibleVersion]]
   * @see [[getHighestCompatibleJdkForSbt]]
   */
  def getHighestCompatibleJdkForSbt(jdk: JavaVersion, sbtVersion: SbtVersion): Option[JavaVersion] = {
    val isCompatible = isSbtAndJdkVersionCompatible(jdk, sbtVersion)
    if (isCompatible) None
    else getHighestCompatibleJdkForSbt(sbtVersion)
  }
}
