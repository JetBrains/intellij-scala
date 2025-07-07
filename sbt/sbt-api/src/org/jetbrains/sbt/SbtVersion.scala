package org.jetbrains.sbt

import org.jetbrains.plugins.scala.project.Version

import scala.math.Ordering.Implicits.infixOrderingOps

final case class SbtVersion(value: Version) extends MinorVersionGenerator[Version] {
  override def minor: String = value.presentation

  override def generateNewVersion(version: String): Option[Version] = Some(Version(version))

  override def toString: String = minor

  lazy val binaryVersion: Version = SbtVersion.standardBinaryVersion(value)

  def isSbt0: Boolean = minor.startsWith("0")
  def isSbt2: Boolean = minor.startsWith("2")

  def inRange(atLeast: SbtVersion, lessThan: SbtVersion): Boolean =
    value.inRange(atLeast.value, lessThan.value)

  def inRange(atLeast: Version, lessThan: Version): Boolean =
    value.inRange(atLeast, lessThan)
}

object SbtVersion {
  def apply(value: String): SbtVersion = new SbtVersion(Version(value))

  implicit val sbtVersionOrdering: Ordering[SbtVersion] = Ordering.by(_.value)

  object Latest {
    val Sbt_0_13: SbtVersion = SbtVersion("0.13.18")

    // NOTE: when updating the latest sbt version,
    // also update `Versions.sbtVersion` in `project/dependencies.scala` in project definition
    // (but let's keep it sbt 1.x for some time)
    private val Sbt_1_10 = SbtVersion("1.11.3")
    private val Sbt_2_0_Candidate = SbtVersion("2.0.0-M3")

    val Sbt_1: SbtVersion = Sbt_1_10
    val Sbt_2: SbtVersion = Sbt_2_0_Candidate //TODO: replace with stable version once sbt 2 is released
    val Sbt_LatestIncludingUnreleased: SbtVersion = Sbt_2_0_Candidate.ensuring(_ >= Sbt_2)

    /**
     * List of latest stable SBT 1.x versions
     */
    val AllSbt1: Seq[SbtVersion] = Seq(
      SbtVersion("1.0.4"),
      SbtVersion("1.1.6"),
      SbtVersion("1.2.8"),
      SbtVersion("1.3.13"),
      SbtVersion("1.4.9"),
      SbtVersion("1.5.8"),
      SbtVersion("1.6.2"),
      SbtVersion("1.7.3"),
      SbtVersion("1.8.3"),
      SbtVersion("1.9.9"),
      Sbt_1_10
    )

    /**
     * List of latest stable SBT 2.x versions
     */
    val AllSbt2: Seq[SbtVersion] = Seq(
      //TODO: add once sbt 2 is released
    )
  }

  /**
   * @return Binary sbt version according to the default sbt behavior:
   *         - 0.13 for all 0.13.x versions
   *         - 1.0 for all 1.x.y versions
   *         - 2.0 for all 2.x.y versions
   */
  private def standardBinaryVersion(sbtVersion: Version): Version = {
    // 1.0.0 milestones are regarded as not binary compatible by sbt
    if ((sbtVersion ~= Version("1.0.0")) && sbtVersion.presentation.contains("-M"))
      sbtVersion
    // sbt uses binary version x.0 for [x.0,x+1.0]
    else if (sbtVersion.major(1) >= Version("1")) {
      val major = sbtVersion.major(1).presentation
      Version(s"$major.0")
    }
    else
      sbtVersion.major(2)
  }

  /** @note tested in org.jetbrains.sbt.SbtUtilTest */
  def upgradeSbtVersionToTheLatestCompatible(sbtVersion: SbtVersion): SbtVersion = {
    val latestCompat =
      if (sbtVersion.isSbt0) Latest.Sbt_0_13
      else if (sbtVersion.isSbt2) Latest.Sbt_2
      else Latest.Sbt_1
    sbtVersion.max(latestCompat)
  }
}