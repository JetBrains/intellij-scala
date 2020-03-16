package org.jetbrains.plugins.scala
package lang
package parser.parsing
package builder

import com.intellij.lang.PsiBuilder
import com.intellij.lang.impl.PsiBuilderAdapter
import com.intellij.openapi.util.text.StringUtil.isWhiteSpace
import com.intellij.psi.impl.source.resolve.FileContextUtil.CONTAINING_FILE_KEY
import org.jetbrains.plugins.scala.annotator.ScalaHighlightingMode

// TODO: now isScala3 is properly set only in org.jetbrains.plugins.scala.lang.parser.ScalaParser
//  update all ScalaPsiBuilderImpl instantiations passing propper isScala3 value
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

  override def error(messageText: String): Unit =
    if (!ScalaHighlightingMode.isShowErrorsFromCompilerEnabled(getProject))
      super.error(messageText)
}