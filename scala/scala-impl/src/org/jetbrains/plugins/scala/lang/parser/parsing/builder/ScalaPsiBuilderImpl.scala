package org.jetbrains.plugins.scala
package lang
package parser
package parsing
package builder

import com.intellij.lang.PsiBuilder
import com.intellij.lang.impl.PsiBuilderAdapter
import com.intellij.openapi.util.text.StringUtil.isWhiteSpace
import com.intellij.psi.impl.source.resolve.FileContextUtil.CONTAINING_FILE_KEY

// TODO: now isScala3 is properly set only in org.jetbrains.plugins.scala.lang.parser.ScalaParser
//  update all ScalaPsiBuilderImpl instantiations passing proper isScala3 value
class ScalaPsiBuilderImpl(delegate: PsiBuilder, override val isScala3: Boolean) extends PsiBuilderAdapter(delegate)
  with ScalaPsiBuilder {

  import lexer.ScalaTokenType._
  import lexer.ScalaTokenTypes._
  import project._

  private var newlinesEnabled = List.empty[Boolean]

  private lazy val containingFile = Option {
    myDelegate.getUserData(CONTAINING_FILE_KEY)
  }

  override def skipExternalToken(): Boolean = false

  override final lazy val isMetaEnabled: Boolean =
    containingFile.exists(_.isMetaEnabled)

  override final lazy val isStrictMode: Boolean =
    containingFile.exists(_.isCompilerStrictMode)

  override final lazy val features: ScalaFeatures = {
    def featuresByVersion(features: ScalaFeatures): ScalaFeatures =
      features.copy(if (isScala3) ScalaVersion.Latest.Scala_3_0 else ScalaVersion.Latest.Scala_2_13)

    // If we don't have a module or a concrete scala version of the file
    // force the version given by isScala
    containingFile.flatMap(_.module) match {
      case Some(module) =>
        val features = module.features
        //
        if (module.scalaMinorVersion.isDefined) features
        else featuresByVersion(features)
      case None => featuresByVersion(ScalaFeatures.default)
    }
  }

  override final lazy val kindProjectUnderscorePlaceholdersOptionEnabled: Boolean =
    containingFile.exists(_.kindProjectorUnderscorePlaceholdersEnabled)

  private lazy val _isTrailingCommasEnabled =
    containingFile.exists(_.isTrailingCommasEnabled)

  private lazy val _isIdBindingEnabled =
    containingFile.exists(_.isIdBindingEnabled)

  override final def isTrailingComma: Boolean = getTokenType match {
    case `tCOMMA` => _isTrailingCommasEnabled
    case _ => false
  }

  override final def isIdBinding: Boolean =
    this.invalidVarId || _isIdBindingEnabled

  override def newlineBeforeCurrentToken: Boolean =
    findPreviousNewLineSafe.isDefined

  override def twoNewlinesBeforeCurrentToken: Boolean =
    findPreviousNewLineSafe.exists { text =>
      s"start $text end".split('\n').exists { line =>
        line.forall(isWhiteSpace)
      }
    }

  override final def disableNewlines(): Unit = {
    newlinesEnabled = false :: newlinesEnabled
  }

  override final def enableNewlines(): Unit = {
    newlinesEnabled = true :: newlinesEnabled
  }

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

  /**
   * Note: if external highlighting in Scala3 is enabled parser errors are not shown
   *
   * @see [[org.jetbrains.plugins.scala.codeInsight.highlighting.ScalaHighlightErrorFilter]]
   * @see [[org.jetbrains.plugins.scala.externalHighlighters.ScalaHighlightingMode.showParserErrors]]
   */
  override def error(messageText: String): Unit =
    super.error(messageText)

  private var indentationStack = List(IndentationWidth.initial)

  def isScala3IndentationBasedSyntaxEnabled: Boolean =
    features.indentationBasedSyntaxEnabled

  override def currentIndentationWidth: IndentationWidth =
    indentationStack.head

  override def pushIndentationWidth(width: IndentationWidth): Unit =
    indentationStack ::= width

  override def popIndentationWidth(): IndentationWidth = {
    val top :: rest = indentationStack
    indentationStack = rest
    top
  }
}