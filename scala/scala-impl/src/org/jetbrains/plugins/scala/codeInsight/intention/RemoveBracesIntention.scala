package org.jetbrains.plugins.scala.codeInsight.intention

import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.{DumbAware, Project}
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.impl.source.codeStyle.CodeEditUtil
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.{PsiComment, PsiElement, PsiWhiteSpace}
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.extensions.{IteratorExt, ObjectExt, PsiElementExt}
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScCaseClause
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScFunctionDefinition, ScPatternDefinition}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory.createBlockExpressionWithoutBracesFromText
import org.jetbrains.plugins.scala.util.IntentionAvailabilityChecker

/**
  * Jason Zaugg
  */
final class RemoveBracesIntention extends PsiElementBaseIntentionAction with DumbAware {

  import RemoveBracesIntention._

  override def getFamilyName: String = ScalaBundle.message("family.name.remove.braces")

  override def getText: String = getFamilyName

  override def isAvailable(project: Project, editor: Editor, element: PsiElement): Boolean =
    check(element).isDefined && IntentionAvailabilityChecker.checkIntention(this, element)

  override def invoke(project: Project, editor: Editor, element: PsiElement): Unit = {
    if (element == null || !element.isValid) return
    check(element).foreach(_.apply())
  }

}

object RemoveBracesIntention {

  def removeBracesIn(maxScope: PsiElement, startElement: PsiElement): Unit = {
    if (startElement != null && startElement.isValid) {
      check(startElement, Option(maxScope)).foreach(_.apply())
    }
  }

  case class CommentsAroundElement(before: Seq[PsiElement], after: Seq[PsiElement])

  def collectComments(element: PsiElement, onElementLine: Boolean = false): CommentsAroundElement = {
    def hasLineBreaks(whiteSpace: PsiElement): Boolean = {
      if (!onElementLine) false
      else StringUtil.containsLineBreak(whiteSpace.getText)
    }

    def getElements(it: Iterator[PsiElement]) = {
      def acceptableElem(elem: PsiElement) = elem.is[PsiComment, PsiWhiteSpace] && !hasLineBreaks(elem)

      it.takeWhile(acceptableElem).filterByType[PsiComment].toSeq
    }

    CommentsAroundElement(getElements(element.prevSiblings).reverse, getElements(element.nextSiblings).reverse)
  }

  def addComments(commentsAroundElement: CommentsAroundElement, parent: PsiElement, anchor: PsiElement): Unit = {
    if ((parent == null) || (anchor == null)) return

    val before = commentsAroundElement.before
    val after = commentsAroundElement.after

    before.foreach(c => CodeEditUtil.setNodeGenerated(c.getNode, true))
    after.foreach(c => CodeEditUtil.setNodeGenerated(c.getNode, true))

    after.foreach(c =>
      if (anchor.getNextSibling != null) parent.getNode.addChild(c.getNode, anchor.getNextSibling.getNode)
      else parent.getNode.addChild(c.getNode)
    )
    before.foreach(c => parent.getNode.addChild(c.getNode, anchor.getNode))
  }

  private def check(element: PsiElement, maxScope: Option[PsiElement] = None): Option[() => Unit] = {
    val classes = Seq(classOf[ScPatternDefinition], classOf[ScIf], classOf[ScFunctionDefinition], classOf[ScTry],
      classOf[ScFinallyBlock], classOf[ScWhile], classOf[ScDo], classOf[ScCaseClause])

    def isAncestorOfElement(ancestor: PsiElement) = PsiTreeUtil.isContextAncestor(ancestor, element, false)

    def isInsideMaxScope(elem: PsiElement) = maxScope.forall(PsiTreeUtil.isAncestor(_, elem, /*strict = */ false))

    val expr: Option[ScExpression] = element.parentOfType(classes).flatMap {
      case ScPatternDefinition.expr(e) if isAncestorOfElement(e) => Some(e)
      case ifStmt: ScIf =>
        ifStmt.thenExpression.filter(isAncestorOfElement).orElse(ifStmt.elseExpression.filter(isAncestorOfElement))
      case funDef: ScFunctionDefinition if !funDef.hasUnitResultType =>
        funDef.body.filter(isAncestorOfElement)
      case tryExpr: ScTry =>
        tryExpr.expression.filter(isAncestorOfElement)
      case finallyBlock: ScFinallyBlock =>
        finallyBlock.expression.filter(isAncestorOfElement)
      case whileStmt: ScWhile =>
        whileStmt.expression.filter(isAncestorOfElement)
      case doStmt: ScDo =>
        doStmt.body.filter(isAncestorOfElement)
      case caseClause: ScCaseClause =>
        caseClause.expr match {
          case Some(x: ScBlockExpr) if isAncestorOfElement(x) && isInsideMaxScope(x) =>
            // special handling for case clauses, which never _need_ braces.
            val action = () => {
              val Regex = """(?ms)\{(.+)\}""".r
              x.getText match {
                case Regex(code) =>
                  val replacement = createBlockExpressionWithoutBracesFromText(code, element)(element)
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
    val oneLinerBlock: Option[(ScBlockExpr, ScExpression, CommentsAroundElement)] = expr.flatMap {
      case blk: ScBlockExpr =>
        blk.statements match {
          case Seq(x: ScExpression) =>
            val comments = collectComments(x, onElementLine = true)
            if (!hasOtherComments(blk, comments) && isInsideMaxScope(blk)) Some((blk, x, comments))
            else None
          case _ => None
        }
      case _ => None
    }

    // Create the action to unwrap that block.
    oneLinerBlock.map {
      case (blkExpr, onlyExpr, comments) =>
        () => {
          addComments(comments, blkExpr.getParent, blkExpr)
          CodeEditUtil.replaceChild(blkExpr.getParent.getNode, blkExpr.getNode, onlyExpr.getNode)
        }
    }
  }

  private[this] def hasOtherComments(element: PsiElement, commentsAroundElement: CommentsAroundElement): Boolean = {
    val allComments = PsiTreeUtil.getChildrenOfTypeAsList(element, classOf[PsiComment])
    allComments.size() > commentsAroundElement.before.size + commentsAroundElement.after.size
  }
}
