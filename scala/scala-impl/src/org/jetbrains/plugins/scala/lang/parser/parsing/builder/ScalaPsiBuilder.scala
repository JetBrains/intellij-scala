package org.jetbrains.plugins.scala.lang
package parser
package parsing
package builder

import com.intellij.lang.PsiBuilder

/**
  * @author Alexander Podkhalyuzin
  */
trait ScalaPsiBuilder extends PsiBuilder {

  def twoNewlinesBeforeCurrentToken: Boolean

  def newlineBeforeCurrentToken: Boolean

  def disableNewlines(): Unit

  def enableNewlines(): Unit

  def restoreNewlinesState(): Unit

  def isTrailingCommasEnabled: Boolean

  def isIdBindingEnabled: Boolean

  def isMetaEnabled: Boolean
}