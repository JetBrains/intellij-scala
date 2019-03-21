package org.jetbrains.plugins.scala.lang
package parser
package parsing
package builder

import com.intellij.lang.PsiBuilder
import com.intellij.lang.impl.PsiBuilderAdapter
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.PsiFile
import com.intellij.psi.impl.source.resolve.FileContextUtil
import com.intellij.testFramework.LightVirtualFileBase
import org.jetbrains.plugins.scala.project.Version
import org.jetbrains.plugins.scala.settings.ScalaProjectSettings
import org.jetbrains.plugins.scala.util.ScalaUtil

import scala.annotation.tailrec
import scala.collection.mutable
import scala.meta.intellij.psi

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
    import psi._
    _.isMetaEnabled(getProject)
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

  override final def isTrailingCommasEnabled: Boolean =
    ScalaPsiBuilderImpl.isTrailingCommasEnabled(getProject, maybePsiFile, maybeScalaVersion)

  override final def isIdBindingEnabled: Boolean =
    maybePsiFile.exists { file =>
      ScalaPsiBuilderImpl.isTestFile(file) ||
        maybeScalaVersion.exists(_ >= Version("2.12"))
    }

  protected final def isNewlinesEnabled: Boolean = newlinesEnabled.isEmpty || newlinesEnabled.top

  final def findPreviousNewLine: Option[String] = whiteSpacesAndComments(1)

  private def previousNewLineExists(predicate: String => Boolean): Boolean =
    isNewlinesEnabled &&
      !eof &&
      canStartStatement &&
      findPreviousNewLine.exists(predicate)

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

  @tailrec
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

object ScalaPsiBuilderImpl {

  // TODO: move to more appropriate place, the code is reused in formatter
  def isTrailingCommasEnabled(file: PsiFile): Boolean = {
    if (file == null) false else isTrailingCommasEnabled(
      file.getProject,
      Some(file),
      ScalaUtil.getScalaVersion(file).map(Version(_))
    )
  }

  private def isTrailingCommasEnabled(project: Project, maybePsiFile: Option[PsiFile], maybeScalaVersion: Option[Version]): Boolean = {
    import ScalaProjectSettings.TrailingCommasMode._
    ScalaProjectSettings.getInstance(project).getTrailingCommasMode match {
      case Enabled => true
      case Disabled => false
      case Auto => maybePsiFile.exists(isTestFile) || maybeScalaVersion.forall(_ >= Version("2.12.2"))
    }
  }

  private def isTestFile(file: PsiFile): Boolean =
    ApplicationManager.getApplication.isUnitTestMode &&
      file.getVirtualFile.isInstanceOf[LightVirtualFileBase]
}