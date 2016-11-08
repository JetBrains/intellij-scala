package org.jetbrains.plugins.scala
package codeInsight.generation

import com.intellij.lang.LanguageCodeInsightActionHandler
import com.intellij.openapi.editor.{Editor, ScrollType}
import com.intellij.openapi.project.Project
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.psi.{PsiDocumentManager, PsiFile}
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil.getCompanionModule
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef._
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory._

/**
 * Nikolay.Tropin
 * 8/17/13
 */
class ScalaGenerateCompanionObjectHandler extends LanguageCodeInsightActionHandler {
  def isValidFor(editor: Editor, file: PsiFile): Boolean =
    file != null && ScalaFileType.INSTANCE == file.getFileType &&
            GenerationUtil.classOrTraitAtCaret(editor, file).exists(canAddCompanionObject)

  def invoke(project: Project, editor: Editor, file: PsiFile) {
    val classOpt = GenerationUtil.classOrTraitAtCaret(editor, file)
    for (clazz <- classOpt) {
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

  def startInWriteAction(): Boolean = true

  private def canAddCompanionObject(clazz: ScTypeDefinition): Boolean =
    getCompanionModule(clazz).isEmpty

  private def createCompanionObject(clazz: ScTypeDefinition): ScObject = {
    if (canAddCompanionObject(clazz)) {
      createObjectWithContext(s"object ${clazz.name} {\n \n}", clazz.getContext, clazz)
    }
    else throw new IllegalArgumentException("Cannot create companion object")
  }

}
