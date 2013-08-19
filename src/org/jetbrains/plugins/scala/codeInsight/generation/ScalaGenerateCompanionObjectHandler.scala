package org.jetbrains.plugins.scala
package codeInsight.generation

import com.intellij.lang.LanguageCodeInsightActionHandler
import com.intellij.openapi.project.Project
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiFile
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScObject, ScClass, ScTrait, ScTemplateDefinition}
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory

/**
 * Nikolay.Tropin
 * 8/17/13
 */
class ScalaGenerateCompanionObjectHandler extends LanguageCodeInsightActionHandler {
  def isValidFor(editor: Editor, file: PsiFile): Boolean =
    file != null && ScalaFileType.SCALA_FILE_TYPE == file.getFileType &&
            canAddCompanionObject(GenerationUtil.getClassAtCaret(editor, file))

  def invoke(project: Project, editor: Editor, file: PsiFile) {
    val clazz = GenerationUtil.getClassAtCaret(editor, file)
    if (clazz == null) return
    val obj = createCompanionObject(clazz)
    val parent = clazz.getParent
    val addedObj = parent.addAfter(obj, clazz)
    parent.addAfter(ScalaPsiElementFactory.createNewLine(clazz.getManager), clazz)
    val offset = addedObj.getTextRange.getStartOffset
    val document = editor.getDocument
    val lineInside = document.getLineNumber(offset) + 1
    editor.getCaretModel.moveToOffset(document.getLineEndOffset(lineInside))
  }

  def startInWriteAction(): Boolean = true

  private def canAddCompanionObject(clazz: ScTemplateDefinition): Boolean = clazz match {
    case c: ScClass if c.isCase => false
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
