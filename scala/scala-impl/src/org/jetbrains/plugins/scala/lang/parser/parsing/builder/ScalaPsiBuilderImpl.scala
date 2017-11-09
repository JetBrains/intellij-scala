package org.jetbrains.plugins.scala.lang.parser.parsing.builder

import com.intellij.lang.PsiBuilder
import com.intellij.lang.impl.PsiBuilderAdapter
import com.intellij.psi.impl.source.resolve.FileContextUtil
import org.jetbrains.plugins.scala.lang.parser.util.ParserUtils
import org.jetbrains.plugins.scala.util.ScalaUtil
import org.jetbrains.plugins.scala.project.Version

import scala.collection.mutable

/**
  * @author Alexander Podkhalyuzin
  */

class ScalaPsiBuilderImpl(builder: PsiBuilder)
  extends PsiBuilderAdapter(builder) with ScalaPsiBuilder {
  private final val newlinesEnabled: mutable.Stack[Boolean] = new mutable.Stack[Boolean]
  
  private lazy val trailingCommasEnabled = ParserUtils.getPsiFile(this).flatMap(
    ScalaUtil.getScalaVersion).map(Version(_)).exists(v => v >= Version("2.12.2")) 

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
  
  def isTrailingCommasEnabled: Boolean = trailingCommasEnabled
}