package org.jetbrains.plugins.scala
package codeInsight.generation

import com.intellij.codeInsight.CodeInsightActionHandler
import com.intellij.codeInsight.generation.actions.BaseGenerateAction
import com.intellij.lang.LanguageCodeInsightActionHandler
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi._
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTemplateDefinition

/**
 * Nikolay.Tropin
 * 8/17/13
 */
abstract class ScalaBaseGenerateAction(val handler: CodeInsightActionHandler)
        extends BaseGenerateAction(handler) {

  override protected def isValidForFile(project: Project, editor: Editor, file: PsiFile): Boolean = {
    lazy val classOk = {
      val targetClass: PsiClass = getTargetClass(editor, file)
      targetClass != null && isValidForClass(targetClass)
    }
    lazy val handlerIsValid = handler match {
      case l: LanguageCodeInsightActionHandler => l.isValidFor(editor, file)
      case _ => true
    }
    file.isInstanceOf[ScalaFile] && file.isWritable && classOk && handlerIsValid
  }

  override protected def isValidForClass(targetClass: PsiClass): Boolean = true

  override protected def getTargetClass(editor: Editor, file: PsiFile): PsiClass = {
    val offset: Int = editor.getCaretModel.getOffset
    val element: PsiElement = file.findElementAt(offset)
    PsiTreeUtil.getParentOfType(element, classOf[ScTemplateDefinition])
  }

}
