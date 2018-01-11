package org.jetbrains.plugins.scala.lang.parser.parsing.builder

import com.intellij.lang.PsiBuilder
import com.intellij.lang.impl.PsiBuilderAdapter
import com.intellij.openapi.project.DumbService
import org.jetbrains.plugins.scala.lang.parser.util.ParserUtils
import org.jetbrains.plugins.scala.lang.parser.util.ParserUtils.getPsiFile
import org.jetbrains.plugins.scala.lang.psi.stubs.elements.ScStubElementType
import org.jetbrains.plugins.scala.project.Version
import org.jetbrains.plugins.scala.util.ScalaUtil

import scala.collection.mutable
import scala.meta.intellij.IdeaUtil

/**
  * @author Alexander Podkhalyuzin
  */

class ScalaPsiBuilderImpl(builder: PsiBuilder)
  extends PsiBuilderAdapter(builder) with ScalaPsiBuilder {
  private final val newlinesEnabled: mutable.Stack[Boolean] = new mutable.Stack[Boolean]
  
  private lazy val scalaVersion: Option[Version] = ParserUtils.getPsiFile(this).flatMap(ScalaUtil.getScalaVersion).map(Version(_))
  private lazy val hasMeta: Boolean = 
    !ScStubElementType.isStubBuilding &&
    !DumbService.isDumb(getProject) && getPsiFile(this).exists {
      file => IdeaUtil.inModuleWithParadisePlugin(file)
    }
  
  def newlineBeforeCurrentToken: Boolean = {
    countNewlineBeforeCurrentToken() > 0
  }

  def twoNewlinesBeforeCurrentToken: Boolean = {
    countNewlineBeforeCurrentToken() > 1
  }

  /**
    * @return 0 if new line is disabled here, or there is no \n chars between tokens
    *         1 if there is no blank lines between tokens
    *         2 otherwise
    */
  private def countNewlineBeforeCurrentToken(): Int = {
    if (newlinesEnabled.nonEmpty && !newlinesEnabled.top) return 0
    if (eof) return 0
    if (!ParserUtils.elementCanStartStatement(getTokenType, this)) return 0

    ParserUtils.countNewLinesBeforeCurrentTokenRaw(this)
  }

  def isNewlinesEnabled: Boolean = newlinesEnabled.isEmpty || newlinesEnabled.top

  def disableNewlines() {
    newlinesEnabled.push(false)
  }

  def enableNewlines() {
    newlinesEnabled.push(true)
  }

  def restoreNewlinesState() {
    assert(newlinesEnabled.nonEmpty)
    newlinesEnabled.pop()
  }

  override def isTrailingCommasEnabled: Boolean = scalaVersion.exists(_ >= Version("2.12.2"))

  override def isIdBindingEnabled: Boolean = scalaVersion.exists(_ >= Version("2.12"))

  override def isMetaEnabled: Boolean = hasMeta
}