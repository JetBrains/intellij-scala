package org.jetbrains.plugins.scala.editor

import com.intellij.psi.tree.IElementType
import com.intellij.psi.{PsiElement, PsiErrorElement}
import org.jetbrains.plugins.scala.editor.enterHandler.Scala3IndentationBasedSyntaxEnterHandler
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScFunctionDefinition, ScPatternDefinition, ScVariableDefinition}

import scala.util.matching.Regex

/**
 * @todo rename this utility, it's not about "auto brace" it's about "indentation syntax
 *       Maybe just merge with [[org.jetbrains.plugins.scala.editor.ScalaIndentationSyntaxUtils]]?
 *
 * Also see [[org.jetbrains.plugins.scala.util.IndentUtil]]
 */
object AutoBraceUtils {
  def nextExpressionInIndentationContext(element: PsiElement): Option[ScExpression] = {
    element.nextSiblingNotWhitespaceComment match {
      case Some(e) => toIndentedExpression(e)
      case _ => None
    }
  }

  def previousExpressionInIndentationContext(element: PsiElement): Option[ScExpression] = {
    val orgStartOffset = element.endOffset
    val lastRealElement = Scala3IndentationBasedSyntaxEnterHandler.getLastRealElement(element)

    // can be null e.g. if typing in the beginning of worksheet file
    if (lastRealElement == null)
      None
    else if (lastRealElement.is[PsiErrorElement])
      None
    else {
      lastRealElement
        .withParentsInFile
        .takeWhile(_.endOffset <= orgStartOffset)
        .flatMap(toIndentedExpression)
        .nextOption()
    }
  }


  private def toIndentedExpression(element: PsiElement): Option[ScExpression] = element match {
    case expr: ScExpression if isIndentationContext(element) && isPrecededByIndent(element) =>
      Some(expr)
    case _ =>
      None
  }

  private def isPrecededByIndent(element: PsiElement): Boolean = {
    element.getPrevSibling.nullSafe.exists(_.textContains('\n'))
  }

  def isIndentationContext(element: PsiElement): Boolean = element.getParent match {
    case ScReturn(`element`) => true
    case ScIf(_, thenBranch, elseBranch) if thenBranch.contains(element) || elseBranch.contains(element) => true
    case ScWhile(_, Some(`element`)) => true
    case ScPatternDefinition.expr(`element`) => true
    case ScVariableDefinition.expr(`element`) => true
    case ScFunctionDefinition.withBody(`element`) => true
    case ScFor(_, `element`) => true
    case ScTry(Some(`element`), _, _) => true
    case ScFinallyBlock(`element`) => true
    case _ => false
  }

  def isBeforeIndentationContext(element: PsiElement): Boolean = {
    val parent = element.getParent
    val isLastInParent = element.endOffset == parent.endOffset
    isLastInParent && (
      parent match {
        case _: ScReturn |
             _: ScIf |
             _: ScWhile |
             _: ScPatternDefinition |
             _: ScVariableDefinition |
             _: ScFunctionDefinition |
             _: ScFor |
             _: ScTry |
             _: ScFinallyBlock => true
        case _ => false
      }
    )
  }

  private val ifIndentationContextContinuation = Set(ScalaTokenTypes.kELSE)
  private val tryIndentationContextContinuation = Set(ScalaTokenTypes.kCATCH, ScalaTokenTypes.kFINALLY)

  def indentationContextContinuation(element: PsiElement): Set[IElementType] = element.getParent match {
    case ScIf(_, Some(`element`), _) => ifIndentationContextContinuation
    case ScTry(Some(`element`), _, _) => tryIndentationContextContinuation
    case _ => Set.empty
  }

  def canBeContinuedWith(element: PsiElement, continuationChar: Char): Boolean = element match {
    case ScIf(_, _, None) if continuationChar == 'e' => true
    case ScTry(_, None, None) if continuationChar == 'c' => true
    case ScTry(_, _, None) if continuationChar == 'f'  => true
    case _ => false
  }


  val indentationContextContinuationsElements: Set[IElementType] = Set(
    ScalaTokenTypes.kELSE,
    ScalaTokenTypes.kCATCH,
    ScalaTokenTypes.kFINALLY
  )

  def continuesConstructAfterIndentationContext(elem: PsiElement): Boolean =
    indentationContextContinuationsElements.contains(elem.elementType)


  val indentationContextContinuationsTexts: Set[String] =
    indentationContextContinuationsElements.map(_.toString)

  def continuesConstructAfterIndentationContext(elementText: String): Boolean =
    indentationContextContinuationsTexts.contains(elementText)


  private val continuationPrefixRegexPattern = new Regex(
    indentationContextContinuationsTexts
      .iterator
      .map(_.foldRight("")("(" + _ + _ + "?)"))
      .mkString("|")
  ).pattern

  def couldBeContinuationAfterIndentationContext(keywordPrefix: String): Boolean = {
    val matcher = continuationPrefixRegexPattern.matcher(keywordPrefix)
    matcher.matches()
  }

  def startsWithContinuationPrefix(text: String): Boolean =
    continuationPrefixRegexPattern.matcher(text).lookingAt()
}
