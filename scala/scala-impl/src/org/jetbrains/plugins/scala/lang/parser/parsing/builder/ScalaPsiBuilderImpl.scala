package org.jetbrains.plugins.scala.lang.parser.parsing.builder

import com.intellij.lang.PsiBuilder
import com.intellij.lang.impl.PsiBuilderAdapter
import com.intellij.openapi.util.text.StringUtil.isWhiteSpace
import com.intellij.psi.impl.source.resolve.FileContextUtil.CONTAINING_FILE_KEY
import org.jetbrains.plugins.scala.lang.parser.IndentationWidth
import org.jetbrains.plugins.scala.project.ProjectPsiFileExt.enableFeaturesCheckInTests
import org.jetbrains.plugins.scala.project._
import org.jetbrains.plugins.scala.{ScalaVersion, isUnitTestMode}

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

  final lazy val scalaLanguageLevel: ScalaLanguageLevel = features.languageLevel

  final private lazy val isAtLeast2_12: Boolean = scalaLanguageLevel >= ScalaLanguageLevel.Scala_2_12

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

  private def enterBracedRegion(): Unit =
    inBracedRegion += 1

  private def exitBracedRegion(): Unit = {
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

  def isScala3IndentationBasedSyntaxEnabled: Boolean =
    features.indentationBasedSyntaxEnabled

  private var indentationRegionStack = List(IndentationRegion.initial)

  override def currentIndentationRegion: IndentationRegion = indentationRegionStack.head

  override def pushIndentationRegion(region: IndentationRegion): Unit = {
    region match {
      case IndentationRegion.Indented(indent) =>
        assert(currentIndentationRegion.isIndent(indent))
      case _: IndentationRegion.Braced =>
        // braced regions can have a lower indentation than the current indentation
        enterBracedRegion()
      case _: IndentationRegion.SingleExpr =>
        // single expression regions have the indentation level of the previous region
      case _: IndentationRegion.BracelessCaseClause =>
        // braceless case clauses have the indentation level of the previous region
    }

    indentationRegionStack ::= region
  }

  override def popIndentationRegion(region: IndentationRegion): IndentationRegion = {
    val popped :: rest = indentationRegionStack
    assert(popped == region)
    indentationRegionStack = rest

    if (popped.isBraced) {
      exitBracedRegion()
    }

    popped
  }


  private val indentationCache = mutable.HashMap.empty[Int, Option[IndentationWidth]]

  /**
   * Return
   *   - Some(Indent[2]) for `  <caret>foo`
   *   - Some(Indent[2]) for `  /* comment */ <caret>foo`
   *   - None for `def foo = <caret>bar`
   */
  def findPrecedingIndentation: Option[IndentationWidth] = {
    indentationCache.getOrElseUpdate(getCurrentOffset, lookBehindForPrecedingIndentation(this, 0))
  }
}