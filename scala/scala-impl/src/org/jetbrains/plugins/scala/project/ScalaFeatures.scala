package org.jetbrains.plugins.scala.project

import com.intellij.openapi.util.{Key, Ref}
import com.intellij.psi.{PsiElement, PsiFile}
import org.jetbrains.annotations.Nullable
import org.jetbrains.plugins.scala.ScalaVersion
import org.jetbrains.plugins.scala.extensions.PsiFileExt
import org.jetbrains.plugins.scala.util.BitMaskStorage

import scala.language.implicitConversions

trait ScalaFeatures extends Any {
  def psiContext: Option[PsiElement]

  def languageLevel: ScalaLanguageLevel
  def isScala3: Boolean = languageLevel.isScala3

  def hasMetaEnabled: Boolean
  def hasTrailingCommasEnabled: Boolean
  def hasUnderscoreWildcardsDisabled: Boolean

  def indentationBasedSyntaxEnabled: Boolean
  def warnAboutDeprecatedInfixCallsEnabled: Boolean

  def `& instead of with`: Boolean
  def `Scala 3 vararg splice syntax`: Boolean
//   wildcards import are bugged in 2.12.14 and 2.13.6
  def `Scala 3 wildcard imports`: Boolean
  def `Scala 3 wildcard imports in selector`: Boolean
  def `Scala 3 renaming imports`: Boolean
  def `soft keywords open and infix`: Boolean
  def `leading infix operator`: Boolean
  def `? as wildcard marker`: Boolean
  def `case in pattern bindings`: Boolean
  def `optional braces for method arguments`: Boolean
  def usingInArgumentsEnabled: Boolean
  def XSourceFlag: ScalaXSourceFlag
}

object ScalaFeatures {
  // TODO: this will be refactored in 213.x
  final class SerializableScalaFeatures private[ScalaFeatures](private val bits: Int) extends AnyVal with ScalaFeatures {
    override def psiContext: Option[PsiElement] = None

    @inline
    private def `in >= 2.12.14 or 2.13.6 with -XSource:3 or 3`: Boolean =
      Bits.`in >= 2.12.14 or 2.13.6 with -XSource:3 or 3`.read(bits)

    @inline
    private def `in >= 2.12.15 or 2.13.7 with -XSource:3 or 3`: Boolean =
      Bits.`in >= 2.12.15 or 2.13.7 with -XSource:3 or 3`.read(bits)

    @inline
    private def `in >= 2.12.15 or 2.13.7 or 3`: Boolean =
      Bits.`in >= 2.12.15 or 2.13.7 or 3`.read(bits)

    @inline
    private def `in >= 2.12.16 or 2.13.9 or 3`: Boolean =
      Bits.`in >= 2.12.16 or 2.13.9 or 3`.read(bits)

    def languageLevel: ScalaLanguageLevel = Bits.languageLevel.read(bits)
    def XSourceFlag: ScalaXSourceFlag = Bits.XSourceFlag.read(bits)

    private def hasNoIndentFlag: Boolean = Bits.hasNoIndentFlag.read(bits)
    private def hasOldSyntaxFlag: Boolean = Bits.hasOldSyntaxFlag.read(bits)
    private def hasDeprecationFlag: Boolean = Bits.hasDeprecationFlag.read(bits)
    private def hasSourceFutureFlag: Boolean = Bits.hasSourceFutureFlag.read(bits)
    def hasMetaEnabled: Boolean = Bits.hasMetaEnabled.read(bits)
    def hasTrailingCommasEnabled: Boolean = Bits.hasTrailingCommasEnabled.read(bits)
    def hasUnderscoreWildcardsDisabled: Boolean = Bits.hasUnderscoreWildcardsDisabled.read(bits)

    def indentationBasedSyntaxEnabled: Boolean = Bits.indentationBasedSyntaxEnabled.read(bits)
    def warnAboutDeprecatedInfixCallsEnabled: Boolean = Bits.warnAboutDeprecatedInfixCallsEnabled.read(bits)

    def `& instead of with`: Boolean = `in >= 2.12.14 or 2.13.6 with -XSource:3 or 3`
    def `Scala 3 vararg splice syntax`: Boolean = `in >= 2.12.14 or 2.13.6 with -XSource:3 or 3`
    // wildcards import are bugged in 2.12.14 and 2.13.6
    def `Scala 3 wildcard imports`: Boolean = `in >= 2.12.14 or 2.13.6 with -XSource:3 or 3`
    def `Scala 3 wildcard imports in selector`: Boolean = `in >= 2.12.15 or 2.13.7 with -XSource:3 or 3`
    def `Scala 3 renaming imports`: Boolean = `in >= 2.12.14 or 2.13.6 with -XSource:3 or 3`
    def `soft keywords open and infix`: Boolean = `in >= 2.12.14 or 2.13.6 with -XSource:3 or 3`
    def `leading infix operator`: Boolean = `in >= 2.12.14 or 2.13.6 with -XSource:3 or 3`
    def `? as wildcard marker`: Boolean =
      `in >= 2.12.16 or 2.13.9 or 3` || `in >= 2.12.14 or 2.13.6 with -XSource:3 or 3`
    def `case in pattern bindings`: Boolean =
      `in >= 2.12.15 or 2.13.7 or 3` || `in >= 2.12.14 or 2.13.6 with -XSource:3 or 3`

    override def usingInArgumentsEnabled: Boolean = Bits.usingInArgumentsEnabled.read(bits)

    def `optional braces for method arguments`: Boolean =
      indentationBasedSyntaxEnabled && languageLevel >= ScalaLanguageLevel.Scala_3_3

    def copy(
      version:                        ScalaVersion,
      XSourceFlag:                    ScalaXSourceFlag = this.XSourceFlag,
      hasNoIndentFlag:                Boolean = this.hasNoIndentFlag,
      hasOldSyntaxFlag:               Boolean = this.hasOldSyntaxFlag,
      hasDeprecationFlag:             Boolean = this.hasDeprecationFlag,
      hasSourceFutureFlag:            Boolean = this.hasSourceFutureFlag,
      hasMetaEnabled:                 Boolean = this.hasMetaEnabled,
      hasTrailingCommasEnabled:       Boolean = this.hasTrailingCommasEnabled,
      hasUnderscoreWildcardsDisabled: Boolean = this.hasUnderscoreWildcardsDisabled
    ): SerializableScalaFeatures =
      ScalaFeatures(
        version = version,
        XSourceFlag = XSourceFlag,
        hasNoIndentFlag = hasNoIndentFlag,
        hasOldSyntaxFlag = hasOldSyntaxFlag,
        hasDeprecationFlag = hasDeprecationFlag,
        hasSourceFutureFlag = hasSourceFutureFlag,
        hasMetaEnabled = hasMetaEnabled,
        hasTrailingCommasEnabled = hasTrailingCommasEnabled,
        hasUnderscoreWildcardsDisabled = hasUnderscoreWildcardsDisabled
      )

    def serializeToInt: Int = bits
  }

  final case class PsiContextFeatures(@Nullable psi: PsiElement, delegate: ScalaFeatures) extends ScalaFeatures {
    override val psiContext: Option[PsiElement] = Option(psi)

    override def languageLevel: ScalaLanguageLevel               = delegate.languageLevel
    override def hasMetaEnabled: Boolean                         = delegate.hasMetaEnabled
    override def hasTrailingCommasEnabled: Boolean               = delegate.hasTrailingCommasEnabled
    override def hasUnderscoreWildcardsDisabled: Boolean         = delegate.hasUnderscoreWildcardsDisabled
    override def indentationBasedSyntaxEnabled: Boolean          = delegate.indentationBasedSyntaxEnabled
    override def warnAboutDeprecatedInfixCallsEnabled: Boolean   = delegate.warnAboutDeprecatedInfixCallsEnabled
    override def `& instead of with`: Boolean                    = delegate.`& instead of with`
    override def `Scala 3 vararg splice syntax`: Boolean         = delegate.`Scala 3 vararg splice syntax`
    override def `Scala 3 wildcard imports`: Boolean             = delegate.`Scala 3 wildcard imports`
    override def `Scala 3 wildcard imports in selector`: Boolean = delegate.`Scala 3 wildcard imports in selector`
    override def `Scala 3 renaming imports`: Boolean             = delegate.`Scala 3 renaming imports`
    override def `soft keywords open and infix`: Boolean         = delegate.`soft keywords open and infix`
    override def `leading infix operator`: Boolean               = delegate.`leading infix operator`
    override def `? as wildcard marker`: Boolean                 = delegate.`? as wildcard marker`
    override def `case in pattern bindings`: Boolean             = delegate.`case in pattern bindings`
    override def usingInArgumentsEnabled: Boolean                = delegate.usingInArgumentsEnabled
    override def `optional braces for method arguments`: Boolean = delegate.`optional braces for method arguments`
    override def XSourceFlag: ScalaXSourceFlag             = delegate.XSourceFlag
  }

  private val minorVersion6  = Version("6")
  private val minorVersion9  = Version("9")
  private val minorVersion12 = Version("12")
  private val minorVersion14 = Version("14")
  private val minorVersion16 = Version("16")
  private val minorVersion18 = Version("18")

  private val ScalaVersion_2_12_2 = ScalaVersion.Latest.Scala_2_12.withMinor(2)

  def deserializeFromInt(bits: Int): SerializableScalaFeatures = new SerializableScalaFeatures(bits)

  def apply(version: ScalaVersion,
            XSourceFlag: ScalaXSourceFlag,
            hasNoIndentFlag: Boolean,
            hasOldSyntaxFlag: Boolean,
            hasDeprecationFlag: Boolean,
            hasSourceFutureFlag: Boolean,
            hasMetaEnabled: Boolean,
            hasTrailingCommasEnabled: Boolean,
            hasUnderscoreWildcardsDisabled: Boolean): SerializableScalaFeatures = {

    val source3OrSource3Cross = XSourceFlag match {
      case ScalaXSourceFlag.XSource3 | ScalaXSourceFlag.XSource3Cross => true
      case ScalaXSourceFlag.None                                      => false
    }

    val languageLevel = version.languageLevel
    val isScala3 = languageLevel.isScala3

    val `in >= 2.12.14 or 2.13.6 with -XSource:3 or 3`: Boolean =
      forMinorVersion(version, isScala3, _ >= minorVersion14 && source3OrSource3Cross, _ >= minorVersion6 && source3OrSource3Cross)
    val `in >= 2.12.15 or 2.13.7 with -XSource:3 or 3`: Boolean =
      forMinorVersion(version, isScala3, _ > minorVersion14 && source3OrSource3Cross, _ > minorVersion6 && source3OrSource3Cross)
    val `in >= 2.12.15 or 2.13.7 or 3`: Boolean =
      forMinorVersion(version, isScala3, _ > minorVersion14, _ > minorVersion6)
    val `in >= 2.12.16 or 2.13.9 or 3`: Boolean =
      forMinorVersion(version, isScala3, _ >= minorVersion16, _ >= minorVersion9)

    // http://dotty.epfl.ch/docs/reference/other-new-features/indentation.html
    // Significant indentation is enabled by default.
    // It can be turned off by giving any of the options -no-indent, -old-syntax and -language:Scala2
    // (NOTE: looks like -language:Scala2 doesn't affect anything in the compiler)
    val indentationBasedSyntaxEnabled: Boolean =
      isScala3 && !(hasNoIndentFlag || hasOldSyntaxFlag)

    val warnAboutDeprecatedInfixCallsEnabled: Boolean =
      isScala3 && hasDeprecationFlag && hasSourceFutureFlag

    val usingInArgumentsEnabled = forMinorVersion(version, isScala3, _ >= minorVersion18, _ >= minorVersion12)

    create(
      languageLevel = languageLevel,
      XSourceFlag   = XSourceFlag,
      hasNoIndentFlag = hasNoIndentFlag,
      hasOldSyntaxFlag = hasOldSyntaxFlag,
      hasDeprecationFlag = hasDeprecationFlag,
      hasSourceFutureFlag = hasSourceFutureFlag,
      hasMetaEnabled = hasMetaEnabled,
      hasTrailingCommasEnabled = hasTrailingCommasEnabled,
      hasUnderscoreWildcardsDisabled = hasUnderscoreWildcardsDisabled,
      indentationBasedSyntaxEnabled = indentationBasedSyntaxEnabled,
      warnAboutDeprecatedInfixCallsEnabled = warnAboutDeprecatedInfixCallsEnabled,
      `in >= 2.12.14 or 2.13.6 with -XSource:3 or 3` = `in >= 2.12.14 or 2.13.6 with -XSource:3 or 3`,
      `in >= 2.12.15 or 2.13.7 with -XSource:3 or 3` = `in >= 2.12.15 or 2.13.7 with -XSource:3 or 3`,
      `in >= 2.12.15 or 2.13.7 or 3` = `in >= 2.12.15 or 2.13.7 or 3`,
      `in >= 2.12.16 or 2.13.9 or 3` = `in >= 2.12.16 or 2.13.9 or 3`,
      usingInArgumentsEnabled = usingInArgumentsEnabled
    )
  }

  val default: SerializableScalaFeatures = onlyByVersion(ScalaVersion.Latest.Scala_2_13)
  private val defaultScala3: SerializableScalaFeatures = onlyByVersion(ScalaVersion.Latest.Scala_3)

  val `-Xsource:3 in 2.12.14 or 2.13.6`: SerializableScalaFeatures = default.copy(
    version = ScalaVersion.Latest.Scala_2_13.withMinor(6),
    XSourceFlag = ScalaXSourceFlag.XSource3
  )

  val `-Xsource:3 in 2.12.15 or 2.13.7`: SerializableScalaFeatures = `-Xsource:3 in 2.12.14 or 2.13.6`
    .copy(ScalaVersion.Latest.Scala_2_13.withMinor(7))


  def onlyByVersion(version: ScalaVersion): SerializableScalaFeatures =
    ScalaFeatures(
      version,
      XSourceFlag = ScalaXSourceFlag.None,
      hasNoIndentFlag = false,
      hasOldSyntaxFlag = false,
      hasDeprecationFlag = false,
      hasSourceFutureFlag = false,
      hasMetaEnabled = false,
      hasTrailingCommasEnabled = version >= ScalaVersion_2_12_2,
      hasUnderscoreWildcardsDisabled = false
    )

  val CreatedWithScalaFeatures: Key[ScalaFeatures] =
    Key.create[ScalaFeatures]("created.with.scala.features")

  def getAttachedScalaFeatures(file: PsiFile): Option[ScalaFeatures] =
    Option(file.getUserData(CreatedWithScalaFeatures))

  def setAttachedScalaFeatures(file: PsiFile, features: ScalaFeatures): Unit =
    file.putUserData(CreatedWithScalaFeatures, features)

  private def forPsi(psi: PsiElement): Option[ScalaFeatures] = {
    val containingFile = Option(psi.getContainingFile)
    containingFile.map(forFile)
  }

  /**
   * @note It may happen that psi element (and containing psi file),
   *       that is being passed as a context to some `ScalaPsiElementFactory` method is already synthetic.
   *       In this case we just use ScalaFeatures stored in/attached to containing file userdata.
   *       However not all synthetic/non-physical files are created by ScalaPsiElementFactory.
   *       That's why we need to fallback to features extracted from module
   *       Examples:
   *        - temp files, created for intention preview
   *        - in-memory files created in light unit tests
   */
  @inline
  private def forFile(file: PsiFile): ScalaFeatures = {
    val featuresAttached =
      if (!file.isPhysical)
        getAttachedScalaFeatures(file)
      else
        None
    val featuresAttachedOrFromModule = featuresAttached.orElse(file.module.map(_.features))
    val result = featuresAttachedOrFromModule.getOrElse(if (file.isScala3File) defaultScala3 else default)
    result
  }

  implicit def forPsiOrDefault(@Nullable psi: PsiElement): ScalaFeatures = {
    val delegate =
      if (psi eq null) ScalaFeatures.default
      else             forPsi(psi).getOrElse(default)

    PsiContextFeatures(psi, delegate)
  }

  def forParserTests(version: ScalaVersion): ScalaFeatures =
    default.copy(
      version = version,
      hasTrailingCommasEnabled = true,
      hasMetaEnabled = true
    )

  def version: Int = Bits.version

  private def forMinorVersion[T](version: ScalaVersion, default: T, in12: Version => T, in13: Version => T): T =
    version.languageLevel match {
      case ScalaLanguageLevel.Scala_2_12 => in12(version.minorVersion)
      case ScalaLanguageLevel.Scala_2_13 => in13(version.minorVersion)
      case _ => default
    }

  private def create(
    languageLevel:                                  ScalaLanguageLevel,
    XSourceFlag:                                    ScalaXSourceFlag,
    hasNoIndentFlag:                                Boolean,
    hasOldSyntaxFlag:                               Boolean,
    hasDeprecationFlag:                             Boolean,
    hasSourceFutureFlag:                            Boolean,
    hasMetaEnabled:                                 Boolean,
    hasTrailingCommasEnabled:                       Boolean,
    hasUnderscoreWildcardsDisabled:                 Boolean,
    indentationBasedSyntaxEnabled:                  Boolean,
    warnAboutDeprecatedInfixCallsEnabled:           Boolean,
    `in >= 2.12.14 or 2.13.6 with -XSource:3 or 3`: Boolean,
    `in >= 2.12.15 or 2.13.7 with -XSource:3 or 3`: Boolean,
    `in >= 2.12.15 or 2.13.7 or 3`:                 Boolean,
    `in >= 2.12.16 or 2.13.9 or 3`:                 Boolean,
    usingInArgumentsEnabled:                        Boolean
  ): SerializableScalaFeatures = {
    val bits = Ref.create[Int]

    Bits.languageLevel.write(bits, languageLevel)
    Bits.XSourceFlag.write(bits, XSourceFlag)
    Bits.hasNoIndentFlag.write(bits, hasNoIndentFlag)
    Bits.hasOldSyntaxFlag.write(bits, hasOldSyntaxFlag)
    Bits.hasDeprecationFlag.write(bits, hasDeprecationFlag)
    Bits.hasSourceFutureFlag.write(bits, hasSourceFutureFlag)
    Bits.hasMetaEnabled.write(bits, hasMetaEnabled)
    Bits.hasTrailingCommasEnabled.write(bits, hasTrailingCommasEnabled)
    Bits.hasUnderscoreWildcardsDisabled.write(bits, hasUnderscoreWildcardsDisabled)
    Bits.indentationBasedSyntaxEnabled.write(bits, indentationBasedSyntaxEnabled)
    Bits.warnAboutDeprecatedInfixCallsEnabled.write(bits, warnAboutDeprecatedInfixCallsEnabled)
    Bits.`in >= 2.12.14 or 2.13.6 with -XSource:3 or 3`.write(bits, `in >= 2.12.14 or 2.13.6 with -XSource:3 or 3`)
    Bits.`in >= 2.12.15 or 2.13.7 with -XSource:3 or 3`.write(bits, `in >= 2.12.15 or 2.13.7 with -XSource:3 or 3`)
    Bits.`in >= 2.12.15 or 2.13.7 or 3`.write(bits, `in >= 2.12.15 or 2.13.7 or 3`)
    Bits.`in >= 2.12.16 or 2.13.9 or 3`.write(bits, `in >= 2.12.16 or 2.13.9 or 3`)
    Bits.usingInArgumentsEnabled.write(bits, usingInArgumentsEnabled)

    new SerializableScalaFeatures(bits.get())
  }

  //noinspection TypeAnnotation
  private object Bits extends BitMaskStorage {
    val languageLevel                        = jEnum[ScalaLanguageLevel]("languageLevel")
    val XSourceFlag                          = jEnum[ScalaXSourceFlag]("XSourceFlag")
    val hasNoIndentFlag                      = bool("hasNoIndentFlag")
    val hasOldSyntaxFlag                     = bool("hasOldSyntaxFlag")
    val hasDeprecationFlag                   = bool("hasDeprecationFlag")
    val hasSourceFutureFlag                  = bool("hasSourceFutureFlag")
    val indentationBasedSyntaxEnabled        = bool("indentationBasedSyntaxEnabled")
    val hasMetaEnabled                       = bool("hasMetaEnabled")
    val hasTrailingCommasEnabled             = bool("hasTrailingCommaEnabled")
    val hasUnderscoreWildcardsDisabled       = bool("hasUnderscoreWildcardsDisabled")
    val warnAboutDeprecatedInfixCallsEnabled = bool("warnAboutDeprecatedInfixCallsEnabled")

    val `in >= 2.12.14 or 2.13.6 with -XSource:3 or 3` = bool("in >= 2.12.14 or 2.13.6 with -XSource:3 or 3")
    val `in >= 2.12.15 or 2.13.7 with -XSource:3 or 3` = bool("in >= 2.12.15 or 2.13.7 with -XSource:3 or 3")

    val `in >= 2.12.15 or 2.13.7 or 3` = bool("in >= 2.12.15 or 2.13.7 or 3")
    val `in >= 2.12.16 or 2.13.9 or 3` = bool("in >= 2.12.16 or 2.13.9 or 3")

    val usingInArgumentsEnabled = bool("usingInArgumentsEnabled")

    override val version: Int = finishAndMakeVersion()
  }
}
