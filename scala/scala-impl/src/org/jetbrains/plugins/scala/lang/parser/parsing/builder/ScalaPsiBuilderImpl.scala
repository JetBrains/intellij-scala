package org.jetbrains.plugins.scala.lang.parser.parsing.builder

import com.intellij.lang.PsiBuilder
import com.intellij.lang.impl.PsiBuilderAdapter
import com.intellij.openapi.util.text.StringUtil.isWhiteSpace
import com.intellij.psi.impl.source.resolve.FileContextUtil.CONTAINING_FILE_KEY
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.IndentationWidth
import org.jetbrains.plugins.scala.project.ProjectPsiFileExt.enableFeaturesCheckInTests
import org.jetbrains.plugins.scala.project._
import org.jetbrains.plugins.scala.{ScalaVersion, isUnitTestMode}

import scala.annotation.tailrec
import scala.collection.mutable

// TODO: now isScala3 is properly set only in org.jetbrains.plugins.scala.lang.parser.ScalaParser
//  update all ScalaPsiBuilderImpl instantiations passing proper isScala3 value
class ScalaPsiBuilderImpl(
  delegate:              PsiBuilder,
  override val isScala3: Boolean,
  presetFeatures:        Option[ScalaFeatures] = None
) extends PsiBuilderAdapter(delegate)
    with ScalaPsiBuilder {

  import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenType._
  import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes._

  private var newlinesEnabled = List.empty[Boolean]

  private var inBracedRegion: Int = 0

  private var inQuotedPattern: Int = 0

  private lazy val containingFile = Option {
    myDelegate.getUserData(CONTAINING_FILE_KEY)
  }

  override def skipExternalToken(): Boolean = false

  override final lazy val isMetaEnabled: Boolean = features.hasMetaEnabled

  override final lazy val isStrictMode: Boolean =
    containingFile.exists(_.isCompilerStrictMode)

  override final lazy val features: ScalaFeatures = presetFeatures.getOrElse {
    def featuresByVersion: ScalaFeatures = {
      val version = if (isScala3) ScalaVersion.Latest.Scala_3 else ScalaVersion.Latest.Scala_2_13
      ScalaFeatures.onlyByVersion(version)
    }

    containingFile match {
      case Some(file) =>
        val fileModule = file.module
        fileModule match {
          case Some(module) =>
            module.features
          case None         =>
            val featuresFromPusher = ScalaFeaturePusher.getFeatures(file)
            featuresFromPusher.getOrElse(featuresByVersion)
        }
      case None =>
        featuresByVersion
    }
  }

  override final lazy val underscoreWildcardsDisabled: Boolean = features.hasUnderscoreWildcardsDisabled

  final lazy val scalaLanguageLevel: Option[ScalaLanguageLevel] = Option(features.languageLevel)

  final private lazy val isAtLeast2_12: Boolean = scalaLanguageLevel.exists(_ >= ScalaLanguageLevel.Scala_2_12)

  override final def isTrailingComma: Boolean = getTokenType match {
    case `tCOMMA` => features.hasTrailingCommasEnabled ||
      (isUnitTestMode && !enableFeaturesCheckInTests)
    case _ => false
  }

  override final def isIdBinding: Boolean =
    this.invalidVarId || isAtLeast2_12

  override def newlineBeforeCurrentToken: Boolean =
    findPreviousNewLineSafe.isDefined

  override def twoNewlinesBeforeCurrentToken: Boolean =
    findPreviousNewLineSafe.exists { text =>
      s"start $text end".split('\n').exists { line =>
        line.forall(isWhiteSpace)
      }
    }

  override final def disableNewlines(): Unit =
    newlinesEnabled = false :: newlinesEnabled

  override final def enableNewlines(): Unit =
    newlinesEnabled = true :: newlinesEnabled

  override final def enterBracedRegion(): Unit =
    inBracedRegion += 1

  override final def exitBracedRegion(): Unit = {
    assert(isInsideBracedRegion)
    inBracedRegion -= 1
  }

  override final def isInsideBracedRegion: Boolean = inBracedRegion > 0

  override final def enterQuotedPattern(): Unit =
    inQuotedPattern += 1

  override final def exitQuotedPattern(): Unit = {
    assert(inQuotedPattern > 0)
    inQuotedPattern -= 1
  }

  override final def isInQuotedPattern: Boolean =
    inQuotedPattern > 0

  override final def restoreNewlinesState(): Unit = {
    assert(newlinesEnabled.nonEmpty)
    newlinesEnabled = newlinesEnabled.tail
  }

  protected final def isNewlinesEnabled: Boolean =
    newlinesEnabled.isEmpty || newlinesEnabled.head

  private def findPreviousNewLineSafe =
    if (isNewlinesEnabled && canStartStatement)
      this.findPreviousNewLine
    else
      None

  private def canStartStatement: Boolean = getTokenType match {
    case null => false
    case `kCATCH` |
         `kELSE` |
         `kEXTENDS` |
         `kFINALLY` |
         `kMATCH` |
         `kWITH` |
         `kYIELD` |
         `tCOMMA` |
         `tDOT` |
         `tSEMICOLON` |
         `tCOLON` |
         `tASSIGN` |
         `tFUNTYPE` |
         `ImplicitFunctionArrow` |
         `tCHOOSE` |
         `tUPPER_BOUND` |
         `tLOWER_BOUND` |
         `tVIEW` |
         `tINNER_CLASS` |
         `tLSQBRACKET` |
         `tRSQBRACKET` |
         `tRPARENTHESIS` |
         `tRBRACE` => false
    case `kCASE` =>
      this.predict { builder =>
        builder.getTokenType match {
          case ObjectKeyword |
               ClassKeyword |
               `tIDENTIFIER` => true
          case _ =>
            false
        }
      }
    case _ => true
  }

  private var indentationStack = List(IndentationWidth.initial)

  def isScala3IndentationBasedSyntaxEnabled: Boolean =
    features.indentationBasedSyntaxEnabled

  override def currentIndentationWidth: IndentationWidth =
    indentationStack.head

  override def previousIndentationWidth: Option[IndentationWidth] =
    indentationStack match {
      case top :: rest =>
        // sometimes we push the same indentation with multiple times
        // so we filter these away
        rest.find(_ != top)
      case _ => None
    }

  override def pushIndentationWidth(width: IndentationWidth): Unit =
    indentationStack ::= width

  override def popIndentationWidth(): IndentationWidth = {
    val top :: rest = indentationStack
    indentationStack = rest
    top
  }

  private val indentationCache = mutable.HashMap.empty[Int, Option[IndentationWidth]]

  /**
   * Return
   *   - Some(Indent[2]) for `  <caret>foo`
   *   - Some(Indent[2]) for `  /* comment */ <caret>foo`
   *   - None for `def foo = <caret>bar`
   */
  def findPreviousIndent: Option[IndentationWidth] = {
    val originalText = getOriginalText
    @tailrec
    def inner(step: Int, tokenEnd: Int, lastNonWsStart: Int): Option[IndentationWidth] = {
      rawLookup(step) match {
        case c if ScalaTokenTypes.COMMENTS_TOKEN_SET.contains(c) =>
          val commentStart = this.rawTokenTypeStart(step)
          inner(step - 1, commentStart, commentStart)
        case ws if ScalaTokenTypes.WHITES_SPACES_TOKEN_SET.contains(ws) =>
          val wsStart = this.rawTokenTypeStart(step)
          val wsText = originalText.subSequence(wsStart, tokenEnd)
          lastNewLineOffset(wsText) match {
            case Some(newlineOffset) =>
              val indentWs = originalText.subSequence(wsStart + newlineOffset + 1, lastNonWsStart)
              Some(new IndentationWidth(indentWs.toString))
            case None =>
              inner(step - 1, wsStart, lastNonWsStart)
          }
        case _ =>
          None
      }
    }

    val currentOffset = getCurrentOffset
    indentationCache.getOrElseUpdate(currentOffset, inner(-1, currentOffset, currentOffset))
  }

  private def lastNewLineOffset(charSeq: CharSequence): Option[Int] = {
    var i = charSeq.length - 1
    while (i >= 0) {
      charSeq.charAt(i) match {
        case '\n' => return Some(i)
        case _    => i -= 1
      }
    }
    None
  }
}