package org.jetbrains.plugins.scala.lang.parser.parsing.builder

import com.intellij.lang.PsiBuilder
import org.jetbrains.plugins.scala.lang.parser.{ErrMsg, IndentationWidth}
import org.jetbrains.plugins.scala.project.ScalaFeatures

trait ScalaPsiBuilder extends PsiBuilder {

  def twoNewlinesBeforeCurrentToken: Boolean

  def newlineBeforeCurrentToken: Boolean

  def disableNewlines(): Unit

  def enableNewlines(): Unit

  def enterBracedRegion(): Unit

  def exitBracedRegion(): Unit

  def isInsideBracedRegion: Boolean

  def enterQuotedPattern(): Unit

  def exitQuotedPattern(): Unit

  def isInQuotedPattern: Boolean

  def restoreNewlinesState(): Unit

  def isTrailingComma: Boolean

  def isIdBinding: Boolean

  def isMetaEnabled: Boolean

  def skipExternalToken(): Boolean

  def isScala3: Boolean

  def isStrictMode: Boolean

  def features: ScalaFeatures

  def underscoreWildcardsDisabled: Boolean

  def isScala3IndentationBasedSyntaxEnabled: Boolean

  def currentIndentationWidth: IndentationWidth

  def previousIndentationWidth: Option[IndentationWidth]

  def pushIndentationWidth(width: IndentationWidth): Unit

  def popIndentationWidth(): IndentationWidth

  /**
   * Instead of using this method consider using more specific error when possible
   */
  final def wrongExpressionError(): Unit = {
    error(ErrMsg("wrong.expression"))
  }
}