package org.jetbrains.plugins.scala
package codeInsight.generation

import com.intellij.codeInsight.generation.actions.BaseGenerateAction
import com.intellij.psi._
import com.intellij.openapi.project.Project
import com.intellij.openapi.editor.Editor
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTemplateDefinition
import com.intellij.lang.LanguageCodeInsightActionHandler

/**
 * Nikolay.Tropin
 * 8/17/13
 */
abstract class ScalaBaseGenerateAction(val handler: LanguageCodeInsightActionHandler)
        extends BaseGenerateAction(handler) {

  override protected def isValidForFile(project: Project, editor: Editor, file: PsiFile): Boolean = {
    lazy val classOk = {
      PsiDocumentManager.getInstance(project).commitDocument(editor.getDocument)
      val targetClass: PsiClass = getTargetClass(editor, file)
      targetClass != null && isValidForClass(targetClass)
    }
    file.isInstanceOf[ScalaFile] && file.isWritable && classOk
  }

  override protected def isValidForClass(targetClass: PsiClass): Boolean = true

  override protected def getTargetClass(editor: Editor, file: PsiFile): PsiClass = {
    val offset: Int = editor.getCaretModel.getOffset
    val element: PsiElement = file.findElementAt(offset)
    PsiTreeUtil.getParentOfType(element, classOf[ScTemplateDefinition])
  }

}
