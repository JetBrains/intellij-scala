package org.jetbrains.plugins.scala.lang.parser.parsing.builder

import com.intellij.lang.PsiBuilder
import org.jetbrains.plugins.scala.lang.parser.{ErrMsg, IndentationWidth}
import org.jetbrains.plugins.scala.project.ScalaFeatures

trait ScalaPsiBuilder extends PsiBuilder {

  def twoNewlinesBeforeCurrentToken: Boolean

  def newlineBeforeCurrentToken: Boolean

  def disableNewlines(): Unit

  def enableNewlines(): Unit

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

  def isScala3IndentationBasedSyntaxEnabled: Boolean

  def findPrecedingIndentation: Option[IndentationWidth]

  def currentIndentationRegion: IndentationRegion

  def pushIndentationRegion(region: IndentationRegion): Unit

  def popIndentationRegion(region: IndentationRegion): Unit

  def allPreviousIndentations(region: IndentationRegion): Set[IndentationWidth]

  /**
   * Parses `body` and returns how many errors were created while parsing.
   * Markers from the outside should not be used inside and markers from the inside
   * should be completed before returning.
   */
  def countDoneErrorsIn[T](body: => T): (Int, T)

  /**
   * Instead of using this method consider using more specific error when possible
   */
  final def wrongExpressionError(): Unit = {
    error(ErrMsg("wrong.expression"))
  }

  final def expressionExpectedError(): Unit = {
    error(ErrMsg("expression.expected"))
  }
}