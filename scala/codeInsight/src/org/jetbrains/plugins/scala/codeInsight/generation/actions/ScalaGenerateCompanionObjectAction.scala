package org.jetbrains.plugins.scala
package codeInsight
package generation
package actions

import com.intellij.openapi.editor.{Editor, ScrollType}
import com.intellij.openapi.project.Project
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.psi.{PsiDocumentManager, PsiFile}
import org.jetbrains.plugins.scala.codeInsight.ScalaCodeInsightBundle
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil.getCompanionModule
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScClass, ScObject, ScTrait, ScTypeDefinition}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory.{createNewLine, createObjectWithContext}

final class ScalaGenerateCompanionObjectAction extends ScalaBaseGenerateAction(
  new ScalaGenerateCompanionObjectAction.Handler,
  ScalaCodeInsightBundle.message("generate.companion.object.action.text"),
  ScalaCodeInsightBundle.message("generate.companion.object.action.description")
)

object ScalaGenerateCompanionObjectAction {

  private[generation] final class Handler extends ScalaCodeInsightActionHandler {

    import Handler._

    override def isValidFor(editor: Editor, file: PsiFile): Boolean =
      super.isValidFor(editor, file) &&
        findClassOrTraitAtCaret(editor, file).exists(canAddCompanionObject)

    override def invoke(project: Project,
                        editor: Editor,
                        file: PsiFile): Unit =
      for (clazz <- findClassOrTraitAtCaret(editor, file)) {
        val obj = createCompanionObject(clazz)
        val parent = clazz.getParent
        val addedObj = parent.addAfter(obj, clazz)
        parent.addAfter(createNewLine()(clazz.getManager), clazz)

        val document = editor.getDocument
        PsiDocumentManager.getInstance(project).doPostponedOperationsAndUnblockDocument(document)
        val offset = addedObj.getTextRange.getStartOffset
        val lineInside = document.getLineNumber(offset) + 1
        CodeStyleManager.getInstance(project).adjustLineIndent(document, document.getLineStartOffset(lineInside))
        editor.getCaretModel.moveToOffset(document.getLineEndOffset(lineInside))
        editor.getScrollingModel.scrollToCaret(ScrollType.MAKE_VISIBLE)
      }
  }


  private object Handler {

    private def findClassOrTraitAtCaret(implicit editor: Editor, file: PsiFile): Option[ScTypeDefinition] =
      elementOfTypeAtCaret(classOf[ScClass], classOf[ScTrait])

    private def canAddCompanionObject(clazz: ScTypeDefinition): Boolean =
      getCompanionModule(clazz).isEmpty

    private def createCompanionObject(clazz: ScTypeDefinition): ScObject = {
      if (canAddCompanionObject(clazz)) {
        createObjectWithContext(s"object ${clazz.name} {\n \n}", clazz.getContext, clazz)
      }
      else throw new IllegalArgumentException("Cannot create companion object")
    }
  }

}
