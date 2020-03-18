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

  def isTrailingComma: Boolean

  def isIdBinding: Boolean

  def isMetaEnabled: Boolean

  def skipExternalToken(): Boolean

  def isScala3: Boolean

  def isStrictMode: Boolean

  def currentIndentionWidth: IndentionWidth

  def withIndentionWidth[R](width: IndentionWidth)(body: => R): R
}