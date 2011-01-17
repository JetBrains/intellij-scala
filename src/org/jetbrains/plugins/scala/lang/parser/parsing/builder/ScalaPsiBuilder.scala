package org.jetbrains.plugins.scala.lang.parser.parsing.builder

import com.intellij.lang.PsiBuilder

/**
 * @author Alexander Podkhalyuzin
 */

trait ScalaPsiBuilder extends PsiBuilder {
  def countNewlineBeforeCurrentToken: Int
  def newlineBeforeCurrentToken: Boolean
  def disableNewlines: Unit
  def enableNewlines: Unit
  def restoreNewlinesState: Unit
}