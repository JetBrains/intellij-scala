package org.jetbrains.plugins.scala.lang
package parser
package parsing
package builder

import com.intellij.lang.PsiBuilder
import com.intellij.lang.impl.PsiBuilderAdapter
import com.intellij.openapi.project.DumbService
import org.jetbrains.plugins.scala.lang.parser.util.ParserUtils
import org.jetbrains.plugins.scala.lang.psi.stubs.elements.ScStubElementType
import org.jetbrains.plugins.scala.project.Version
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

  private lazy val maybeScalaVersion =
    getPsiFile(this)
      .flatMap(ScalaUtil.getScalaVersion)
      .map(Version(_))

  override lazy val isMetaEnabled: Boolean =
    !ScStubElementType.isStubBuilding &&
      !DumbService.isDumb(getProject) &&
      getPsiFile(this).exists(IdeaUtil.inModuleWithParadisePlugin)

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

  override final def isTrailingCommasEnabled: Boolean =
    maybeScalaVersion.forall(_ >= Version("2.12.2"))

  override final def isIdBindingEnabled: Boolean =
    maybeScalaVersion.exists(_ >= Version("2.12"))

  protected final def isNewlinesEnabled: Boolean = newlinesEnabled.isEmpty || newlinesEnabled.top

  /**
    * @return 0 if new line is disabled here, or there is no \n chars between tokens
    *         1 if there is no blank lines between tokens
    *         2 otherwise
    */
  private def countNewlineBeforeCurrentToken =
    if (isNewlinesEnabled &&
      !eof &&
      elementCanStartStatement(getTokenType, this))
      countNewLinesBeforeCurrentTokenRaw(this)
    else
      0
}