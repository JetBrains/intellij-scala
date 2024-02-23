package org.jetbrains.plugins.scala
package codeInsight
package intention
package argument

import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.tree.IElementType
import com.intellij.psi.util.PsiUtil
import com.intellij.psi.{PsiElement, PsiWhiteSpace}
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScParameters
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory.createExpressionFromText
import org.jetbrains.plugins.scala.project.ProjectContext
import org.jetbrains.plugins.scala.util.IntentionAvailabilityChecker

final class BlockExpressionToArgumentIntention extends PsiElementBaseIntentionAction {
  override def getFamilyName: String = ScalaCodeInsightBundle.message("family.name.convert.to.argument.in.parentheses")

  override def getText: String = getFamilyName

  override def isAvailable(project: Project, editor: Editor, element: PsiElement): Boolean =
    element match {
      case Parent((block: ScBlockExpr) & Parent(list: ScArgumentExprList))
        if list.exprs.size == 1 && block.caseClauses.isEmpty =>
        IntentionAvailabilityChecker.checkIntention(this, element) && producesSameResult(block, element)
      case _ => false
    }


  private def producesSameResult(block: ScBlockExpr, element: PsiElement): Boolean = {
    def withoutBlock(e: PsiElement): Seq[PsiElement] = e match {
      case block: ScBlock => block.statements.flatMap(withoutBlock)
      case _ => Seq(e)
    }
    def withoutFunctionExpr(e: PsiElement): Seq[PsiElement] = e match {
      case ScFunctionExpr(_, Some(result)) =>
        result.asOptionOf[ScBlock]
          .map(withoutBlock)
          .getOrElse(Seq(result))
      case _ =>
        Seq(e)
    }

    singleExpressionStatement(block).exists { statement =>
      implicit val context: ProjectContext = block.getManager
      buildNewArgumentsText(statement)
        .flatMap(createExpressionFromText(_, element).children.findByType[ScArgumentExprList])
        .exists { newArguments =>
          isStructurallyEqual(
            withoutFunctionExpr(newArguments.exprs.head),
            withoutFunctionExpr(statement)
          )
        }
    }
  }

  private def isStructurallyEqual(a: Seq[PsiElement], b: Seq[PsiElement]): Boolean = {
    def convert(psi: Seq[PsiElement]): Seq[String] =
      psi.iterator
        .flatMap(_.breadthFirst())
        .filterNot(_.isWhitespaceOrComment)
        .map(_.toString.filterNot(_.isWhitespace))
        .to(LazyList)
    convert(a) == convert(b)
  }

  // ScFunctionExpr
  private def singleExpressionStatement(block: ScBlock): Option[ScBlockStatement] = {
    block.statements match {
      case Seq(statement) => Some(statement)
      case _ => None
    }
  }

  override def invoke(project: Project, editor: Editor, element: PsiElement): Unit = {
    val block = element.getParent.asInstanceOf[ScBlockExpr]
    implicit val context: ProjectContext = block.getManager

    for {
      statement <- singleExpressionStatement(block)
      newArgumentsText <- buildNewArgumentsText(statement)
      newArguments <- createExpressionFromText(newArgumentsText, element).children.findByType[ScArgumentExprList]
      replacement = block.getParent.replace(newArguments)
    } replacement.getPrevSibling match {
      case ws: PsiWhiteSpace => ws.delete()
      case _ =>
    }
  }

  private def buildNewArgumentsText(stmt: ScBlockStatement)
                                   (implicit context: ProjectContext): Option[String] = stmt match {
    case function: ScFunctionExpr =>
      buildNewFunctionalArguments(function)
    case expr: ScExpression =>
      Some(s"foo(${expr.getText})")
    case _ =>
      None
  }

  private def buildNewFunctionalArguments(function: ScFunctionExpr)
                                         (implicit context: ProjectContext): Option[String] = {
    val params: ScParameters = function.params
    for {
      body <- function.result
      newParamsText: String = {
        val hasSomeType = function.parameters.exists(_.paramType.isDefined)
        val newParentheses = hasSomeType && !params.startsWithToken(ScalaTokenTypes.tLPARENTHESIS)
        params.getText.parenthesize(newParentheses)
      }
      newBodyText: String = {
        val bodyText = body.getText
        val needBraces = bodyText.contains('\n') && !body.startsWithToken(ScalaTokenTypes.tLBRACE)
        bodyText.braced(needBraces)
      }
    } yield s"foo($newParamsText => $newBodyText)"
  }
}
