package org.jetbrains.plugins.scala.lang
package parser
package parsing
package builder

import com.intellij.lang.PsiBuilder
import com.intellij.lang.impl.PsiBuilderAdapter
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.impl.source.resolve.FileContextUtil
import com.intellij.testFramework.LightVirtualFileBase
import org.jetbrains.plugins.scala.project.Version
import org.jetbrains.plugins.scala.settings.ScalaProjectSettings
import org.jetbrains.plugins.scala.util.ScalaUtil

import scala.annotation.tailrec
import scala.collection.mutable
import scala.meta.intellij.psiExt._

/**
  * @author Alexander Podkhalyuzin
  */
class ScalaPsiBuilderImpl(delegate: PsiBuilder)
  extends PsiBuilderAdapter(delegate) with ScalaPsiBuilder {

  private val newlinesEnabled = new mutable.Stack[Boolean]

  private lazy val maybePsiFile = Option {
    myDelegate.getUserData(FileContextUtil.CONTAINING_FILE_KEY)
  }

  private lazy val maybeScalaVersion =
    maybePsiFile.flatMap(ScalaUtil.getScalaVersion)
      .map(Version(_))

  override lazy val isMetaEnabled: Boolean = maybePsiFile.exists {
    _.isMetaEnabled(getProject)
  }

  override def newlineBeforeCurrentToken: Boolean =
    checkedFindPreviousNewLine.isDefined

  override def twoNewlinesBeforeCurrentToken: Boolean =
    checkedFindPreviousNewLine.toSeq.flatMap { text =>
      s"start $text end".split('\n')
    }.exists(ScalaPsiBuilderImpl.isBlank)

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

  override final def isTrailingCommasEnabled: Boolean = {
    import ScalaProjectSettings.TrailingCommasMode._
    ScalaProjectSettings.getInstance(getProject).getTrailingCommasMode match {
      case Enabled => true
      case Auto => isTestFile || maybeScalaVersion.forall(_ >= Version("2.12.2"))
      case Disabled => false
    }
  }

  override final def isIdBindingEnabled: Boolean =
    isTestFile || maybeScalaVersion.exists(_ >= Version("2.12"))

  protected final def isNewlinesEnabled: Boolean = newlinesEnabled.isEmpty || newlinesEnabled.top

  final def findPreviousNewLine: Option[String] = {
    @tailrec
    def whiteSpacesAndComments(steps: Int = 1): String =
      if (steps < getCurrentOffset && TokenSets.WHITESPACE_OR_COMMENT_SET.contains(rawLookup(-steps)))
        whiteSpacesAndComments(steps + 1)
      else
        originalSubText(1 - steps)


    whiteSpacesAndComments() match {
      case text if text.contains('\n') => Some(text)
      case _ => None
    }
  }

  private def checkedFindPreviousNewLine =
    if (isNewlinesEnabled &&
      !eof &&
      canStartStatement) findPreviousNewLine
    else None

  private def isTestFile =
    ApplicationManager.getApplication.isUnitTestMode &&
      maybePsiFile.exists(_.getVirtualFile.isInstanceOf[LightVirtualFileBase])

  import lexer.{ScalaTokenTypes => T}

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
      val marker = mark
      advanceLexer()

      val result = getTokenType match {
        case T.kOBJECT |
             T.kCLASS => true
        case _ => false
      }

      marker.rollbackTo()
      result
    case _ => true
  }

  private def originalSubText(steps: Int) = getOriginalText.subSequence(
    rawTokenTypeStart(steps),
    rawTokenTypeStart(0)
  ).toString
}

object ScalaPsiBuilderImpl {

  private def isBlank(string: String) =
    string.forall(StringUtil.isWhiteSpace)
}