package org.jetbrains.plugins.scala
package codeInsight.intention

import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.impl.source.codeStyle.CodeEditUtil
import com.intellij.psi.tree.TokenSet
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScCaseClause
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScFunctionDefinition, ScPatternDefinition}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import org.jetbrains.plugins.scala.util.IntentionAvailabilityChecker

/**
 * Jason Zaugg
 */

class RemoveBracesIntention extends PsiElementBaseIntentionAction {
  def getFamilyName = "Remove braces"

  override def getText = getFamilyName

  def isAvailable(project: Project, editor: Editor, element: PsiElement) = 
    check(project, editor, element).isDefined && IntentionAvailabilityChecker.checkIntention(this, element)

  override def invoke(project: Project, editor: Editor, element: PsiElement) {
    if (element == null || !element.isValid) return
    check(project, editor, element) match {
      case Some(x) => x()
      case None =>
    }
  }

  private def check(project: Project, editor: Editor, element: PsiElement): Option[() => Unit] = {
    val containing = ScalaPsiUtil.getParentOfType(element, true,
      classOf[ScPatternDefinition], classOf[ScIfStmt], classOf[ScFunctionDefinition], classOf[ScTryBlock],
      classOf[ScFinallyBlock], classOf[ScWhileStmt], classOf[ScDoStmt], classOf[ScCaseClause])

    def isAncestorOfElement(ancestor: PsiElement) = PsiTreeUtil.isContextAncestor(ancestor, element, false)

    val expr: Option[ScExpression] = containing match {
      case pattern @ ScPatternDefinition.expr(e) if isAncestorOfElement(e) => Some(e)
      case ifStmt: ScIfStmt =>
        ifStmt.thenBranch.filter(isAncestorOfElement).orElse(ifStmt.elseBranch.filter(isAncestorOfElement))
      case funDef: ScFunctionDefinition if !funDef.hasUnitResultType =>
        funDef.body.filter(isAncestorOfElement)
      case tryBlock: ScTryBlock if tryBlock.hasRBrace =>
        // special handling for try block, which itself is parent to the (optional) pair of braces.
        val lBrace = tryBlock.getNode.getChildren(TokenSet.create(ScalaTokenTypes.tLBRACE))
        val rBrace = tryBlock.getNode.getChildren(TokenSet.create(ScalaTokenTypes.tRBRACE))
        (lBrace, rBrace) match {
          case (Array(lBraceNode), Array(rBraceNode)) if tryBlock.statements.length == 1 =>
            val action = () => {
              Seq(lBraceNode, rBraceNode).foreach(tryBlock.getNode.removeChild)
              CodeEditUtil.markToReformat(tryBlock.getParent.getNode, true)
              // TODO clean up excess newlines.
            }
            return Some(action)
          case _ => None
        }
      case finallyBlock: ScFinallyBlock =>
        finallyBlock.expression.filter(isAncestorOfElement)
      case whileStmt: ScWhileStmt =>
        whileStmt.body.filter(isAncestorOfElement)
      case doStmt: ScDoStmt =>
        doStmt.getExprBody.filter(isAncestorOfElement)
      case caseClause: ScCaseClause =>
        caseClause.expr match {
          case Some(x: ScBlockExpr) if isAncestorOfElement(x) =>
            // special handling for case clauses, which never _need_ braces.
            val action = () => {
              val Regex = """(?ms)\{(.+)\}""".r
              x.getText match {
                case Regex(code) =>
                  val replacement = ScalaPsiElementFactory.createBlockExpressionWithoutBracesFromText(code, element.getManager)
                  CodeEditUtil.replaceChild(x.getParent.getNode, x.getNode, replacement.getNode)
                  CodeEditUtil.markToReformat(caseClause.getNode, true)
                case _ =>
                  ()
              }
            }
            return Some(action)
          case _ =>
            None
        }
      case _ => None
    }

    // Everything other than case clauses is treated uniformly.

    // Is the expression a block containing a single expression?
    val oneLinerBlock: Option[(ScBlockExpr, ScExpression)] = expr.flatMap {
      case blk: ScBlockExpr =>
        blk.statements match {
          case Seq(x: ScExpression) => Some((blk, x))
          case _ => None
        }
      case _ => None
    }

    // Create the action to unwrap that block.
    oneLinerBlock.map {
      case (blkExpr, onlyExpr) =>
        () => {
          CodeEditUtil.replaceChild(blkExpr.getParent.getNode, blkExpr.getNode, onlyExpr.getNode)
        }
    }
  }
}
