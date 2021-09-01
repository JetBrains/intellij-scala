package org.jetbrains.plugins.scala.project

import com.intellij.openapi.util.Ref
import org.jetbrains.plugins.scala.ScalaVersion
import org.jetbrains.plugins.scala.project.ScalaFeatures.Bits
import org.jetbrains.plugins.scala.util.BitMaskStorage

// TODO: this will be refactored in 213.x
final class ScalaFeatures private(private val bits: Int) extends AnyVal {
  @inline
  private def `in >= 2.12.14 or 2.13.6 with -XSource:3 or 3`: Boolean =
    Bits.`in >= 2.12.14 or 2.13.6 with -XSource:3 or 3`.read(bits)

  @inline
  private def `in >= 2.12.15 or 2.13.7 with -XSource:3 or 3`: Boolean =
    Bits.`in >= 2.12.15 or 2.13.7 with -XSource:3 or 3`.read(bits)

  @inline
  private def `in >= 2.12.15 or 2.13.7 or 3`: Boolean =
    Bits.`in >= 2.12.15 or 2.13.7 or 3`.read(bits)


  def languageLevel: ScalaLanguageLevel = Bits.languageLevel.read(bits)
  def isScala3: Boolean = languageLevel >= ScalaLanguageLevel.Scala_3_0

  private def hasSource3Flag: Boolean = Bits.hasSource3Flag.read(bits)
  private def hasNoIndentFlag: Boolean = Bits.hasNoIndentFlag.read(bits)
  private def hasOldSyntaxFlag: Boolean = Bits.hasOldSyntaxFlag.read(bits)

  def indentationBasedSyntaxEnabled: Boolean = Bits.indentationBasedSyntaxEnabled.read(bits)

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
  def `case in pattern bindings`: Boolean =
    `in >= 2.12.15 or 2.13.7 or 3` || `in >= 2.12.14 or 2.13.6 with -XSource:3 or 3`

  def copy(version: ScalaVersion,
           hasSource3Flag: Boolean = this.hasSource3Flag,
           hasNoIndentFlag: Boolean = this.hasNoIndentFlag,
           hasOldSyntaxFlag: Boolean = this.hasOldSyntaxFlag): ScalaFeatures =
    ScalaFeatures(
      version = version,
      hasSource3Flag = hasSource3Flag,
      hasNoIndentFlag = hasNoIndentFlag,
      hasOldSyntaxFlag = hasOldSyntaxFlag,
    )

  def serializeToInt: Int = bits
}

object ScalaFeatures {
  private val minorVersion14 = Version("14")
  private val minorVersion6 = Version("6")

  def deserializeFromInt(bits: Int): ScalaFeatures = new ScalaFeatures(bits)

  def apply(version: ScalaVersion,
            hasSource3Flag: Boolean,
            hasNoIndentFlag: Boolean,
            hasOldSyntaxFlag: Boolean): ScalaFeatures = {
    val languageLevel = version.languageLevel
    val isScala3 = languageLevel >= ScalaLanguageLevel.Scala_3_0

    val `in >= 2.12.14 or 2.13.6 with -XSource:3 or 3`: Boolean =
      forMinorVersion(version, isScala3, _ >= minorVersion14 && hasSource3Flag, _ >= minorVersion6 && hasSource3Flag)
    val `in >= 2.12.15 or 2.13.7 with -XSource:3 or 3`: Boolean =
      forMinorVersion(version, isScala3, _ > minorVersion14 && hasSource3Flag, _ > minorVersion6 && hasSource3Flag)
    val `in >= 2.12.15 or 2.13.7 or 3`: Boolean =
      forMinorVersion(version, isScala3, _ > minorVersion14, _ > minorVersion6)

    // http://dotty.epfl.ch/docs/reference/other-new-features/indentation.html
    // Significant indentation is enabled by default.
    // It can be turned off by giving any of the options -no-indent, -old-syntax and -language:Scala2
    // (NOTE: looks like -language:Scala2 doesn't affect anything in the compiler)
    val indentationBasedSyntaxEnabled: Boolean =
      isScala3 && !(hasNoIndentFlag || hasOldSyntaxFlag)

    create(
      languageLevel = languageLevel,
      hasSource3Flag,
      hasNoIndentFlag,
      hasOldSyntaxFlag,
      indentationBasedSyntaxEnabled = indentationBasedSyntaxEnabled,
      `in >= 2.12.14 or 2.13.6 with -XSource:3 or 3` = `in >= 2.12.14 or 2.13.6 with -XSource:3 or 3`,
      `in >= 2.12.15 or 2.13.7 with -XSource:3 or 3` = `in >= 2.12.15 or 2.13.7 with -XSource:3 or 3`,
      `in >= 2.12.15 or 2.13.7 or 3` = `in >= 2.12.15 or 2.13.7 or 3`
    )
  }

  val default: ScalaFeatures = onlyByVersion(ScalaVersion.Latest.Scala_2_13)

  val `-Xsource:3 in 2.12.14 or 2.13.6`: ScalaFeatures = default.copy(
    version = ScalaVersion.Latest.Scala_2_13.withMinor(6),
    hasSource3Flag = true
  )

  val `-Xsource:3 in 2.12.15 or 2.13.7`: ScalaFeatures = `-Xsource:3 in 2.12.14 or 2.13.6`
    .copy(ScalaVersion.Latest.Scala_2_13.withMinor(7))

  def onlyByVersion(version: ScalaVersion): ScalaFeatures =
    ScalaFeatures(version, hasSource3Flag = false, hasNoIndentFlag = false, hasOldSyntaxFlag = false)

  def version: Int = Bits.version

  private def forMinorVersion[T](version: ScalaVersion, default: T, in12: Version => T, in13: Version => T): T =
    version.languageLevel match {
      case ScalaLanguageLevel.Scala_2_12 => in12(version.minorVersion)
      case ScalaLanguageLevel.Scala_2_13 => in13(version.minorVersion)
      case _ => default
    }

  private def create(languageLevel: ScalaLanguageLevel,
                     hasSource3Flag: Boolean,
                     hasNoIndentFlag: Boolean,
                     hasOldSyntaxFlag: Boolean,
                     indentationBasedSyntaxEnabled: Boolean,
                     `in >= 2.12.14 or 2.13.6 with -XSource:3 or 3`: Boolean,
                     `in >= 2.12.15 or 2.13.7 with -XSource:3 or 3`: Boolean,
                     `in >= 2.12.15 or 2.13.7 or 3`: Boolean): ScalaFeatures = {
    val bits = Ref.create[Int]

    Bits.languageLevel.write(bits, languageLevel)
    Bits.hasSource3Flag.write(bits, hasSource3Flag)
    Bits.hasNoIndentFlag.write(bits, hasNoIndentFlag)
    Bits.hasOldSyntaxFlag.write(bits, hasOldSyntaxFlag)
    Bits.indentationBasedSyntaxEnabled.write(bits, indentationBasedSyntaxEnabled)
    Bits.`in >= 2.12.14 or 2.13.6 with -XSource:3 or 3`.write(bits, `in >= 2.12.14 or 2.13.6 with -XSource:3 or 3`)
    Bits.`in >= 2.12.15 or 2.13.7 with -XSource:3 or 3`.write(bits, `in >= 2.12.15 or 2.13.7 with -XSource:3 or 3`)
    Bits.`in >= 2.12.15 or 2.13.7 or 3`.write(bits, `in >= 2.12.15 or 2.13.7 or 3`)

    new ScalaFeatures(bits.get())
  }


  //noinspection TypeAnnotation
  private object Bits extends BitMaskStorage {
    val languageLevel = jEnum[ScalaLanguageLevel]("languageLevel")
    val hasSource3Flag = bool("hasSource3Flag")
    val hasNoIndentFlag = bool("hasNoIndentFlag")
    val hasOldSyntaxFlag = bool("hasOldSyntaxFlag")
    val indentationBasedSyntaxEnabled = bool("indentationBasedSyntaxEnabled")
    val `in >= 2.12.14 or 2.13.6 with -XSource:3 or 3` = bool("in >= 2.12.14 or 2.13.6 with -XSource:3 or 3")
    val `in >= 2.12.15 or 2.13.7 with -XSource:3 or 3` = bool("in >= 2.12.15 or 2.13.7 with -XSource:3 or 3")
    val `in >= 2.12.15 or 2.13.7 or 3` = bool("in >= 2.12.15 or 2.13.7 or 3")

    override val version: Int = finishAndMakeVersion()
  }
}
