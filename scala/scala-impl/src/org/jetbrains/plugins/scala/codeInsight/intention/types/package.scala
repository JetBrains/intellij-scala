package org.jetbrains.plugins.scala.codeInsight.intention

import com.intellij.codeInsight.template._
import com.intellij.openapi.editor.Editor
import com.intellij.psi.{PsiDocumentManager, PsiElement}

package object types {

  def startTemplate(
    elem: PsiElement,
    context: PsiElement,
    expression: Expression,
    editor: Editor
  ): Unit = {
    startTemplate(elem, context, expression, editor, null)
  }

  def startTemplate(
    elem: PsiElement,
    context: PsiElement,
    expression: Expression,
    editor: Editor,
    listener: TemplateEditingListener,
  ): Unit = {
    val project = context.getProject
    val manager = PsiDocumentManager.getInstance(project)
    manager.commitAllDocuments()
    manager.doPostponedOperationsAndUnblockDocument(editor.getDocument)

    val builder = new TemplateBuilderImpl(elem)
    builder.replaceElement(elem, expression)

    editor.getCaretModel.moveToOffset(elem.getNode.getStartOffset)

    val templateManager = TemplateManager.getInstance(project)
    templateManager.startTemplate(editor, builder.buildInlineTemplate(), listener)
  }
}
