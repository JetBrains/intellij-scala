package org.jetbrains.plugins.scala
package lang
package parser.parsing
package builder

import com.intellij.lang.{PsiBuilder, impl}
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.impl.source.resolve.FileContextUtil

/**
  * @author Alexander Podkhalyuzin
  */
class ScalaPsiBuilderImpl(delegate: PsiBuilder)
  extends impl.PsiBuilderAdapter(delegate)
    with ScalaPsiBuilder {

  import lexer.{ScalaTokenTypes => T}

  implicit def project: Project = getProject

  private val newlinesEnabled = new collection.mutable.Stack[Boolean]

  private lazy val containingFile = Option {
    myDelegate.getUserData(FileContextUtil.CONTAINING_FILE_KEY)
  }

  private lazy val (_isTrailingCommasEnabled, _isIdBindingEnabled) = {
    import util.ScalaUtil.{isIdBindingEnabled => isIdBindingEnabledImpl, isTrailingCommasEnabled => isTrailingCommasEnabledImpl, _}

    val actualVersion = containingFile.flatMap(findScalaVersion)
    (isTrailingCommasEnabledImpl(containingFile)(actualVersion),
      isIdBindingEnabledImpl(containingFile)(actualVersion))
  }

  override def isTrailingComma: Boolean = getTokenType match {
    case T.tCOMMA => _isTrailingCommasEnabled
    case _ => false
  }

  override def isIdBindingEnabled: Boolean = _isIdBindingEnabled

  override lazy val isMetaEnabled: Boolean = containingFile.exists {
    import meta.intellij.psi._
    _.isMetaEnabled
  }

  override def newlineBeforeCurrentToken: Boolean =
    previousNewLineExists(Function.const(true))

  override def twoNewlinesBeforeCurrentToken: Boolean =
    previousNewLineExists { text =>
      s"start $text end".split('\n').exists { line =>
        line.forall(StringUtil.isWhiteSpace)
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

  protected final def isNewlinesEnabled: Boolean = newlinesEnabled.isEmpty || newlinesEnabled.top

  final def findPreviousNewLine: Option[String] = whiteSpacesAndComments(1)

  private def previousNewLineExists(predicate: String => Boolean): Boolean =
    isNewlinesEnabled &&
      !eof &&
      canStartStatement &&
      findPreviousNewLine.exists(predicate)

  private def canStartStatement: Boolean = getTokenType match {
    case T.kCATCH |
         T.kELSE |
         T.kEXTENDS |
         T.kFINALLY |
         T.kMATCH |
         T.kWITH |
         T.kYIELD |
         T.tCOMMA |
         T.tDOT |
         T.tSEMICOLON |
         T.tCOLON |
         T.tASSIGN |
         T.tFUNTYPE |
         T.tCHOOSE |
         T.tUPPER_BOUND |
         T.tLOWER_BOUND |
         T.tVIEW |
         T.tINNER_CLASS |
         T.tLSQBRACKET |
         T.tRSQBRACKET |
         T.tRPARENTHESIS |
         T.tRBRACE => false
    case T.kCASE =>
      this.predict {
        _.getTokenType match {
          case T.kOBJECT | T.kCLASS => true
          case _ => false
        }
      }
    case _ => true
  }

  @annotation.tailrec
  private[this] def whiteSpacesAndComments(steps: Int): Option[String] =
    if (steps < getCurrentOffset && TokenSets.WHITESPACE_OR_COMMENT_SET.contains(rawLookup(-steps))) {
      whiteSpacesAndComments(steps + 1)
    } else {
      val originalSubText = getOriginalText.subSequence(
        rawTokenTypeStart(1 - steps),
        rawTokenTypeStart(0)
      ).toString

      if (originalSubText.contains('\n')) Some(originalSubText)
      else None
    }
}