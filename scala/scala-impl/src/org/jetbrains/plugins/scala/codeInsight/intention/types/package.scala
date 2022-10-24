package org.jetbrains.plugins.scala.codeInsight.intention

import com.intellij.codeInsight.template._
import com.intellij.openapi.editor.Editor
import com.intellij.psi.{PsiDocumentManager, PsiElement}

package object types {

  def startTemplate(elem: PsiElement, context: PsiElement, expression: Expression, editor: Editor): Unit = {
    val project = context.getProject
    val manager = PsiDocumentManager.getInstance(project)
    manager.commitAllDocuments()
    manager.doPostponedOperationsAndUnblockDocument(editor.getDocument)

    val builder = new TemplateBuilderImpl(elem)
    builder.replaceElement(elem, expression)
    editor.getCaretModel.moveToOffset(elem.getNode.getStartOffset)
    TemplateManager.getInstance(project).startTemplate(editor, builder.buildInlineTemplate())
  }
}
