package org.jetbrains.plugins.scala.lang
package parser
package parsing
package builder

import com.intellij.lang.PsiBuilder
import org.jetbrains.plugins.scala.project.{ScalaFeatures, ScalaLanguageLevel}

trait ScalaPsiBuilder extends PsiBuilder {

  def twoNewlinesBeforeCurrentToken: Boolean

  def newlineBeforeCurrentToken: Boolean

  def disableNewlines(): Unit

  def enableNewlines(): Unit

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

  def scalaLanguageLevel: Option[ScalaLanguageLevel]

  def isScala3IndentationBasedSyntaxEnabled: Boolean

  def currentIndentationWidth: IndentationWidth

  def pushIndentationWidth(width: IndentationWidth): Unit

  def popIndentationWidth(): IndentationWidth
}