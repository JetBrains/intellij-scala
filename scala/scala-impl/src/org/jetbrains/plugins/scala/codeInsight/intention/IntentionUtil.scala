package org.jetbrains.plugins.scala.codeInsight.intention

import com.intellij.codeInsight.template._
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.impl.source.codeStyle.CodeEditUtil
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.{PsiComment, PsiDocumentManager, PsiElement, PsiWhiteSpace}
import org.jetbrains.plugins.scala.extensions.PsiElementExt

object IntentionUtil {
  def collectComments(element: PsiElement, onElementLine: Boolean = false): CommentsAroundElement = {
    def hasLineBreaks(whiteSpace: PsiElement): Boolean = {
      if (!onElementLine) false
      else StringUtil.containsLineBreak(whiteSpace.getText)
    }

    def getElements(it: Iterator[PsiElement]) = {
      def acceptableElem(elem: PsiElement) = {
        (elem.isInstanceOf[PsiComment] || elem.isInstanceOf[PsiWhiteSpace]) && !hasLineBreaks(elem)
      }

      it.takeWhile { a => acceptableElem(a) }.filter(a => a.isInstanceOf[PsiComment]).toSeq
    }

    CommentsAroundElement(getElements(element.prevSiblings).reverse, getElements(element.nextSiblings).reverse)
  }


  def hasOtherComments(element: PsiElement, commentsAroundElement: CommentsAroundElement): Boolean = {
    val allComments = PsiTreeUtil.getChildrenOfTypeAsList(element, classOf[PsiComment])
    allComments.size() > commentsAroundElement.before.size + commentsAroundElement.after.size
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

  case class CommentsAroundElement(before: Seq[PsiElement], after: Seq[PsiElement])

  def startTemplate(elem: PsiElement, context: PsiElement, expression: Expression, editor: Editor): Unit = {
    val project = context.getProject
    val manager = PsiDocumentManager.getInstance(project)
    manager.commitAllDocuments()
    manager.doPostponedOperationsAndUnblockDocument(editor.getDocument)
    val builder: TemplateBuilderImpl = new TemplateBuilderImpl(elem)
    builder.replaceElement(elem, expression)
    editor.getCaretModel.moveToOffset(elem.getNode.getStartOffset)
    TemplateManager.getInstance(project).startTemplate(editor, builder.buildInlineTemplate())
  }
}
