package org.jetbrains.plugins.scala
package codeInsight.generation

import com.intellij.lang.LanguageCodeInsightActionHandler
import com.intellij.openapi.editor.{Editor, ScrollType}
import com.intellij.openapi.project.Project
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.psi.{PsiDocumentManager, PsiFile}
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef._
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory

/**
 * Nikolay.Tropin
 * 8/17/13
 */
class ScalaGenerateCompanionObjectHandler extends LanguageCodeInsightActionHandler {
  def isValidFor(editor: Editor, file: PsiFile): Boolean =
    file != null && ScalaFileType.SCALA_FILE_TYPE == file.getFileType &&
            GenerationUtil.classOrTraitAtCaret(editor, file).exists(canAddCompanionObject)

  def invoke(project: Project, editor: Editor, file: PsiFile) {
    val classOpt = GenerationUtil.classOrTraitAtCaret(editor, file)
    for (clazz <- classOpt) {
      val obj = createCompanionObject(clazz)
      val parent = clazz.getParent
      val addedObj = parent.addAfter(obj, clazz)
      parent.addAfter(ScalaPsiElementFactory.createNewLine(clazz.getManager), clazz)
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

  private def canAddCompanionObject(clazz: ScTemplateDefinition): Boolean = clazz match {
    case td: ScTypeDefinition if td.fakeCompanionModule.nonEmpty => false
    case _: ScTrait | _: ScClass => ScalaPsiUtil.getBaseCompanionModule(clazz).isEmpty
    case _ => false
  }

  private def createCompanionObject(clazz: ScTemplateDefinition): ScObject = {
    if (canAddCompanionObject(clazz)) {
      val name = clazz.name
      val text = s"object $name {\n \n}"
      ScalaPsiElementFactory.createObjectWithContext(text, clazz.getContext, clazz)
    }
    else throw new IllegalArgumentException("Cannot create companion object")
  }

}
