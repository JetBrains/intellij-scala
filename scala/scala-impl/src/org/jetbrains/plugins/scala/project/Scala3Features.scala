package org.jetbrains.plugins.scala.project

import org.jetbrains.plugins.scala.ScalaVersion
import org.jetbrains.plugins.scala.project.Scala3Features._

final class Scala3Features(version: ScalaVersion,
                           hasSource3Flag: Boolean,
                           hasNoIndentFlag: Boolean,
                           hasOldSyntaxFlag: Boolean) {
  private val isScala3 = version.languageLevel >= ScalaLanguageLevel.Scala_3_0
  private val `in >= 2.12.14 or 2.13.6 with -XSource:3 or 3`: Boolean =
    forMinorVersion(version, isScala3, _ >= minorVersion14 && hasSource3Flag, _ >= minorVersion6 && hasSource3Flag)
  private val `in >= 2.12.15 or 2.13.7 with -XSource:3 or 3`: Boolean =
    forMinorVersion(version, isScala3, _ > minorVersion14 && hasSource3Flag, _ > minorVersion6 && hasSource3Flag)

  // http://dotty.epfl.ch/docs/reference/other-new-features/indentation.html
  // Significant indentation is enabled by default.
  // It can be turned off by giving any of the options -no-indent, -old-syntax and -language:Scala2
  // (NOTE: looks like -language:Scala2 doesn't affect anything in the compiler)
  val indentationBasedSyntaxEnabled: Boolean =
    isScala3 && !(hasNoIndentFlag || hasOldSyntaxFlag)

  def `disallow auto-eta-expansion of SAMs`: Boolean = !`in >= 2.12.14 or 2.13.6 with -XSource:3 or 3`
  def `& instead of with`: Boolean = `in >= 2.12.14 or 2.13.6 with -XSource:3 or 3`
  def `Scala 3 vararg splice syntax`: Boolean = `in >= 2.12.14 or 2.13.6 with -XSource:3 or 3`
  // wildcards import are bugged in 2.12.14 and 2.13.6
  def `Scala 3 wildcard imports`: Boolean = `in >= 2.12.14 or 2.13.6 with -XSource:3 or 3`
  def `Scala 3 wildcard imports in selector`: Boolean = `in >= 2.12.15 or 2.13.7 with -XSource:3 or 3`
  def `Scala 3 renaming imports`: Boolean = `in >= 2.12.14 or 2.13.6 with -XSource:3 or 3`
  def `soft keywords open and infix`: Boolean = `in >= 2.12.14 or 2.13.6 with -XSource:3 or 3`
  def `leading infix operator`: Boolean = `in >= 2.12.14 or 2.13.6 with -XSource:3 or 3`
  def `? as wildcard marker`: Boolean = `in >= 2.12.14 or 2.13.6 with -XSource:3 or 3`
  def `case in pattern bindings`: Boolean = `in >= 2.12.14 or 2.13.6 with -XSource:3 or 3`

  def inVersion(version: ScalaVersion): Scala3Features =
    new Scala3Features(
      version = version,
      hasSource3Flag = hasSource3Flag,
      hasNoIndentFlag = hasNoIndentFlag,
      hasOldSyntaxFlag = hasOldSyntaxFlag
    )
}

object Scala3Features {
  private val minorVersion14 = Version("14")
  private val minorVersion6 = Version("6")

  def default: Scala3Features = none

  val none: Scala3Features = onlyByVersion(ScalaVersion.Latest.Scala_2_12)

  val `-Xsource:3 in 2.12.14 or 2.13.6`: Scala3Features = new Scala3Features(
    version = ScalaVersion.Latest.Scala_2_13.withMinor(6),
    hasSource3Flag = true,
    hasNoIndentFlag = false,
    hasOldSyntaxFlag = false,
  )

  val `-Xsource:3 in 2.12.15 or 2.13.7`: Scala3Features = `-Xsource:3 in 2.12.14 or 2.13.6`
    .inVersion(ScalaVersion.Latest.Scala_2_13.withMinor(7))

  def onlyByVersion(version: ScalaVersion): Scala3Features =
    new Scala3Features(version, hasSource3Flag = false, hasNoIndentFlag = false, hasOldSyntaxFlag = false)

  private def forMinorVersion[T](version: ScalaVersion, default: T, in12: Version => T, in13: Version => T): T =
    version.languageLevel match {
      case ScalaLanguageLevel.Scala_2_12 => in12(version.minorVersion)
      case ScalaLanguageLevel.Scala_2_13 => in13(version.minorVersion)
      case _ => default
    }
}