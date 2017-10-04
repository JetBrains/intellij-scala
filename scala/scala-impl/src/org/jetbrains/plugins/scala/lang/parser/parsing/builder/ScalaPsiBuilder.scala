package org.jetbrains.plugins.scala.lang.parser.parsing.builder

import com.intellij.lang.PsiBuilder

/**
 * @author Alexander Podkhalyuzin
 */

trait ScalaPsiBuilder extends PsiBuilder {
  def twoNewlinesBeforeCurrentToken: Boolean
  def newlineBeforeCurrentToken: Boolean
  def disableNewlines()
  def enableNewlines()
  def restoreNewlinesState()
}