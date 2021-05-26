package org.jetbrains.plugins.scala
package lang
package formatting
package processors

import com.intellij.openapi.util.TextRange
import com.intellij.psi.codeStyle.CodeStyleSettings
import com.intellij.psi.impl.source.SourceTreeToPsiMap
import com.intellij.psi.impl.source.codeStyle.{CodeEditUtil, PostFormatProcessor, PostFormatProcessorHelper}
import com.intellij.psi.{PsiElement, PsiErrorElement, PsiFile}
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.formatting.settings.ScalaCodeStyleSettings
import org.jetbrains.plugins.scala.lang.formatting.settings.ScalaCodeStyleSettings.TrailingCommaMode
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.api.ScalaRecursiveElementVisitor
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns._
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScTupleTypeElement
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScArgumentExprList, ScBlockExpr, ScTuple, ScTypedExpression}
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.{ScParameterClause, ScTypeParamClause}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports.ScImportExpr
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import org.jetbrains.plugins.scala.project.ProjectPsiFileExt

class ScalaTrailingCommaProcessor extends PostFormatProcessor {
  override def processElement(source: PsiElement, settings: CodeStyleSettings): PsiElement = {
    new ScalaTrailingCommaVisitor(settings).processElement(source)
  }

  override def processText(source: PsiFile, rangeToReformat: TextRange, settings: CodeStyleSettings): TextRange = {
    new ScalaTrailingCommaVisitor(settings).processText(source, rangeToReformat)
  }
}

// Note: we do not support all trailing comma scopes in this processor dues to not all scopes are so important
// Please see detailed discussions of trailing comma support in Scala language:
// https://github.com/scala/docs.scala-lang/pull/533
// https://github.com/scala/scala/pull/5245
// https://github.com/scala/scala/pull/5245#issuecomment-228301429
private class ScalaTrailingCommaVisitor(settings: CodeStyleSettings) extends ScalaRecursiveElementVisitor {
  private val commonSettings = settings.getCommonSettings(ScalaLanguage.INSTANCE)
  private val postProcessor: PostFormatProcessorHelper = new PostFormatProcessorHelper(commonSettings)
  private val scalaSettings = settings.getCustomSettings(classOf[ScalaCodeStyleSettings])

  import scalaSettings._

  def processElement(element: PsiElement): PsiElement = {
    if (skipElement(element.getContainingFile)) {
      element
    } else {
      assert(element.isValid)
      element.accept(this)
      element
    }
  }

  def processText(element: PsiFile, rangeToReformat: TextRange): TextRange = {
    if (skipElement(element)) {
      rangeToReformat
    } else {
      postProcessor.setResultTextRange(rangeToReformat)
      element.accept(this)
      postProcessor.getResultTextRange
    }
  }

  private def skipElement(source: PsiFile): Boolean = {
    scalaSettings.TRAILING_COMMA_MODE == TrailingCommaMode.TRAILING_COMMA_KEEP ||
      scalaSettings.USE_SCALAFMT_FORMATTER ||
      !source.isTrailingCommasEnabled
  }

  override def visitArgumentExprList(args: ScArgumentExprList): Unit = {
    doVisit(args, super.visitArgumentExprList, TRAILING_COMMA_ARG_LIST_ENABLED) {
      _.exprs.lastOption.filter {
        case te: ScTypedExpression if te.isSequenceArg => false
        case _ => true
      }
    }
  }

  override def visitParameterClause(clause: ScParameterClause): Unit = {
    doVisit(clause, super.visitParameterClause, TRAILING_COMMA_PARAMS_ENABLED) {
      _.parameters.lastOption.filterNot(_.isVarArgs)
    }
  }

  override def visitTuple(tuple: ScTuple): Unit = {
    doVisit(tuple, super.visitTuple, TRAILING_COMMA_TUPLE_ENABLED)(_.exprs.lastOption)
  }

  override def visitTupleTypeElement(tuple: ScTupleTypeElement): Unit = {
    doVisit(tuple, super.visitTupleTypeElement, TRAILING_COMMA_TUPLE_TYPE_ENABLED)(_.components.lastOption)
  }

  override def visitPattern(pat: ScPattern): Unit = {
    super.visitPattern(pat)
    pat match {
      case tuple: ScTuplePattern =>
        doVisit(tuple, (_: ScTuplePattern) => (), TRAILING_COMMA_PATTERN_ARG_LIST_ENABLED) {
          _.patternList.flatMap(_.patterns.lastOption)
        }
      case _ =>
    }
  }

  override def visitPatternArgumentList(args: ScPatternArgumentList): Unit = {
    doVisit(args, super.visitPatternArgumentList, TRAILING_COMMA_PATTERN_ARG_LIST_ENABLED) {
      _.patterns.lastOption.filter {
        case _: ScSeqWildcardPattern => false
        case np: ScNamingPattern if np.named.isInstanceOf[ScSeqWildcardPattern] => false
        case _ => true
      }
    }
  }

  override def visitTypeParameterClause(clause: ScTypeParamClause): Unit = {
    doVisit(clause, super.visitTypeParameterClause, TRAILING_COMMA_TYPE_PARAMS_ENABLED)(_.typeParameters.lastOption)
  }

  override def visitImportExpr(importExpr: ScImportExpr): Unit = {
    doVisit(importExpr, super.visitImportExpr, TRAILING_COMMA_IMPORT_SELECTOR_ENABLED)(_.selectors.lastOption)
  }

  private def doVisit[T <: PsiElement](element: T, superDoVisit: T => Any, enabled: Boolean)
                                      (trailingElements: T => Option[PsiElement]): Unit = {
    superDoVisit(element)
    if (checkElementContainsRange(element) && enabled) {
      trailingElements(element).foreach(processTrailingElement)
    }
  }

  private def processTrailingElement[T <: PsiElement](trailingElement: PsiElement): Unit = {
    val next: PsiElement = trailingElement.getNextSiblingNotWhitespaceComment
    if (next != null && next.getNextSibling.isInstanceOf[PsiErrorElement])
      return

    val isCommaNext = next != null && next.getNode.getElementType == ScalaTokenTypes.tCOMMA

    val parent = trailingElement.getParent
    val project = trailingElement.getProject
    val oldTextLength: Int = parent.getTextLength
    try {
      scalaSettings.TRAILING_COMMA_MODE match {
        case TrailingCommaMode.TRAILING_COMMA_REMOVE_WHEN_MULTILINE =>
          if (isCommaNext && next.followedByNewLine()) {
            CodeEditUtil.removeChild(
              SourceTreeToPsiMap.psiElementToTree(parent),
              SourceTreeToPsiMap.psiElementToTree(next)
            )
          }
        case TrailingCommaMode.TRAILING_COMMA_ADD_WHEN_MULTILINE =>
          val isSingleInfixBlockExpression = parent match {
            case args: ScArgumentExprList => args.isSingleInfixBlockExpression
            case _ => false
          }
          if (!isCommaNext && trailingElement.followedByNewLine() && !isSingleInfixBlockExpression) {
            val newComma = ScalaPsiElementFactory.createComma(project)
            CodeEditUtil.addChild(
              SourceTreeToPsiMap.psiElementToTree(parent),
              SourceTreeToPsiMap.psiElementToTree(newComma),
              SourceTreeToPsiMap.psiElementToTree(trailingElement.getNextSibling)
            )
          }
        case _ =>
      }
    } finally {
      updateResultRange(oldTextLength, parent.getTextLength)
    }
  }

  protected def checkElementContainsRange(element: PsiElement): Boolean = {
    postProcessor.isElementPartlyInRange(element)
  }

  protected def updateResultRange(oldTextLength: Int, newTextLength: Int): Unit = {
    postProcessor.updateResultRange(oldTextLength, newTextLength)
  }
}
