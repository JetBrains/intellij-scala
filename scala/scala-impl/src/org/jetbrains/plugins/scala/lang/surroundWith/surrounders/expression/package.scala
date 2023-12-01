package org.jetbrains.plugins.scala.lang.surroundWith.surrounders

import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.psi.{PsiElement, PsiWhiteSpace}
import org.jetbrains.plugins.scala.extensions.{ElementType, OptionExt, PsiElementExt, inWriteCommandAction}
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil.convertBlockToBraceless
import org.jetbrains.plugins.scala.lang.psi.api.ScalaPsiElement
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScBlockExpr, ScCatchBlock, ScFinallyBlock, ScFor, ScIf, ScMatch, ScWhile}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory.{createExpressionFromText, createWhitespace}
import org.jetbrains.plugins.scala.project.{ProjectContext, ProjectExt, ScalaFeatures}

package object expression {

  private[scala] implicit class ScalaPsiElementExt[E <: ScalaPsiElement](private val element: E) extends AnyVal {
    def toIndentationBasedSyntax(implicit ctx: ProjectContext = element.projectContext,
                                 features: ScalaFeatures = element): E = inWriteCommandAction {
      val withNewSyntax = Rewriters.rewriteToNewSyntax(element)
      val withIndentationBasedSyntax = Rewriters.rewriteToIndentationBasedSyntax(withNewSyntax)

      withIndentationBasedSyntax
    }(ctx.project)

    def toNewSyntax(implicit ctx: ProjectContext = element.projectContext,
                    features: ScalaFeatures = element): E =
      inWriteCommandAction(Rewriters.rewriteToNewSyntax(element))(ctx.project)
  }

  private object Rewriters {
    private def addKeyword(statement: ScalaPsiElement, keyword: PsiElement, anchor: PsiElement)
                          (implicit ctx: ProjectContext): Unit = {
      val addedKw = statement.addAfter(keyword, anchor)
      statement.addBefore(createWhitespace, addedKw)
      statement.addAfter(createWhitespace, addedKw)
    }

    def rewriteToNewSyntax[E <: ScalaPsiElement](element: E)
                                                (implicit ctx: ProjectContext, features: ScalaFeatures): E =
      if (!ctx.project.indentationBasedSyntaxEnabled(features)) element
      else element match {
        case ifStmt: ScIf if ifStmt.thenKeyword.isEmpty =>
          convertToBraceless(ifStmt)(_.leftParen, _.rightParen, _.thenKeyword, "if true then ()").asInstanceOf[E]
        case whileStmt: ScWhile if whileStmt.doKeyword.isEmpty || whileStmt.leftParen.nonEmpty =>
          convertToBraceless(whileStmt)(_.leftParen, _.rightParen, _.doKeyword, "while true do ()").asInstanceOf[E]
        case forStmt: ScFor if forStmt.yieldOrDoKeyword.isEmpty || forStmt.getLeftBracket.nonEmpty =>
          convertToBraceless(forStmt)(_.getLeftBracket, _.getRightBracket, _.yieldOrDoKeyword, "for x <- xs do ()").asInstanceOf[E]
        case _ => element
      }

    def rewriteToIndentationBasedSyntax[E <: ScalaPsiElement](element: E)
                                                             (implicit ctx: ProjectContext, features: ScalaFeatures): E =
      if (!ctx.project.indentationBasedSyntaxEnabled(features)) element
      else {
        CodeStyleManager.getInstance(ctx.project).reformat(element, true)

        element match {
          case matchStmt: ScMatch =>
            if (matchStmt.clauses.nonEmpty) {
              matchStmt.children.toList.foreach {
                case brace@ElementType(ScalaTokenTypes.tLBRACE) =>
                  brace.delete()
                case brace@ElementType(ScalaTokenTypes.tRBRACE) =>
                  brace.prevSibling.filterByType[PsiWhiteSpace].foreach(_.delete())
                  brace.delete()
                case _ =>
              }
            }
          case _ =>
            element.children.toList.foreach {
              case block: ScBlockExpr =>
                convertBlockToBraceless(block)
              case catchBlock: ScCatchBlock =>
                catchBlock.expression.foreach {
                  case block: ScBlockExpr =>
                    convertBlockToBraceless(block)
                  case _ =>
                }
              case ScFinallyBlock(block: ScBlockExpr) =>
                convertBlockToBraceless(block)
              case _ =>
            }
        }

        element
      }

    private[this] def convertToBraceless[E <: ScalaPsiElement](element: E)(leftParenOrBrace: E => Option[PsiElement],
                                                                           rightParenOrBrace: E => Option[PsiElement],
                                                                           keyword: E => Option[PsiElement],
                                                                           templateWithKeyword: String)
                                                              (implicit ctx: ProjectContext, features: ScalaFeatures): E = {
      leftParenOrBrace(element).foreach(_.delete())
      rightParenOrBrace(element).foreach { rParenOrBrace =>
        keyword(element) match {
          case Some(_) =>
          case _ =>
            val dummyExpr = createExpressionFromText(templateWithKeyword, features).asInstanceOf[E]
            keyword(dummyExpr)
              .foreach(kw => addKeyword(statement = element, keyword = kw, anchor = rParenOrBrace))
        }
        rParenOrBrace.delete()
      }

      keyword(element).foreach { kw =>
        val endOffset = kw.nextSibling.filterByType[PsiWhiteSpace].getOrElse(kw).endOffset
        CodeStyleManager.getInstance(ctx.project).reformatRange(element, element.startOffset, endOffset, true)
      }

      element
    }
  }
}
