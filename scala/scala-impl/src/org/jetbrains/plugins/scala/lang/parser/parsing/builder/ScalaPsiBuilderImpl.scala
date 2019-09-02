package org.jetbrains.plugins.scala
package lang
package parser.parsing
package builder

import com.intellij.lang.PsiBuilder
import com.intellij.lang.impl.PsiBuilderAdapter
import com.intellij.openapi.util.text.StringUtil.isWhiteSpace
import com.intellij.psi.impl.source.resolve.FileContextUtil.CONTAINING_FILE_KEY

/**
 * @author Alexander Podkhalyuzin
 */
class ScalaPsiBuilderImpl(delegate: PsiBuilder) extends PsiBuilderAdapter(delegate)
  with ScalaPsiBuilder {

  import lexer.ScalaTokenTypes._

  private val newlinesEnabled = new collection.mutable.Stack[Boolean]

  private lazy val containingFile = Option {
    myDelegate.getUserData(CONTAINING_FILE_KEY)
  }

  private lazy val (_isTrailingCommasEnabled, _isIdBindingEnabled) =
    util.ScalaUtil.areTrailingCommasAndIdBindingEnabled(containingFile)(getProject)

  override def isTrailingComma: Boolean = getTokenType match {
    case `tCOMMA` => _isTrailingCommasEnabled
    case _ => false
  }

  override def isIdBinding: Boolean =
    this.invalidVarId || _isIdBindingEnabled

  override lazy val isMetaEnabled: Boolean = containingFile.exists {
    import meta.intellij.psi._
    _.isMetaEnabled(getProject)
  }

  override def newlineBeforeCurrentToken: Boolean =
    findPreviousNewLineSafe.isDefined

  override def twoNewlinesBeforeCurrentToken: Boolean =
    findPreviousNewLineSafe.exists { text =>
      s"start $text end".split('\n').exists { line =>
        line.forall(isWhiteSpace)
      }
    }

  override final def disableNewlines(): Unit = {
    newlinesEnabled.push(false)
  }

  override final def enableNewlines(): Unit = {
    newlinesEnabled.push(true)
  }

  override final def restoreNewlinesState(): Unit = {
    assert(newlinesEnabled.nonEmpty)
    newlinesEnabled.pop()
  }

  protected final def isNewlinesEnabled: Boolean =
    newlinesEnabled.isEmpty || newlinesEnabled.top

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
      this.predict {
        _.getTokenType match {
          case `kOBJECT` | `kCLASS` => true
          case _ => false
        }
      }
    case _ => true
  }
}