package org.jetbrains.plugins.scala.lang
package parser
package parsing
package builder

import com.intellij.lang.PsiBuilder
import com.intellij.lang.impl.PsiBuilderAdapter
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.DumbService
import com.intellij.psi.impl.source.resolve.FileContextUtil
import com.intellij.testFramework.LightVirtualFileBase
import org.jetbrains.plugins.scala.lang.parser.util.ParserUtils
import org.jetbrains.plugins.scala.lang.psi.stubs.elements.ScStubElementType
import org.jetbrains.plugins.scala.project.Version
import org.jetbrains.plugins.scala.settings.ScalaProjectSettings
import org.jetbrains.plugins.scala.util.ScalaUtil

import scala.collection.mutable
import scala.meta.intellij.IdeaUtil

/**
  * @author Alexander Podkhalyuzin
  */
class ScalaPsiBuilderImpl(delegate: PsiBuilder)
  extends PsiBuilderAdapter(delegate) with ScalaPsiBuilder {

  import ParserUtils._

  private val newlinesEnabled = new mutable.Stack[Boolean]

  private lazy val maybePsiFile = Option {
    myDelegate.getUserData(FileContextUtil.CONTAINING_FILE_KEY)
  }

  private lazy val maybeScalaVersion =
    maybePsiFile.flatMap(ScalaUtil.getScalaVersion)
      .map(Version(_))

  override lazy val isMetaEnabled: Boolean =
    !ScStubElementType.isStubBuilding &&
      !DumbService.isDumb(getProject) &&
      maybePsiFile.exists(IdeaUtil.inModuleWithParadisePlugin)

  override def newlineBeforeCurrentToken: Boolean = countNewlineBeforeCurrentToken > 0

  override def twoNewlinesBeforeCurrentToken: Boolean = countNewlineBeforeCurrentToken > 1

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

  /**
    * @return 0 if new line is disabled here, or there is no \n chars between tokens
    *         1 if there is no blank lines between tokens
    *         2 otherwise
    */
  private def countNewlineBeforeCurrentToken =
    if (isNewlinesEnabled &&
      !eof &&
      tokenCanStartStatement)
      countNewLinesBeforeCurrentTokenRaw(this)
    else
      0

  private def isTestFile =
    ApplicationManager.getApplication.isUnitTestMode &&
      maybePsiFile.exists(_.getVirtualFile.isInstanceOf[LightVirtualFileBase])

  import lexer.{ScalaTokenTypes => T}

  private def tokenCanStartStatement: Boolean = getTokenType match {
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
}