package org.jetbrains.plugins.scala
package codeInsight.generation

import com.intellij.codeInsight.generation.actions.BaseGenerateAction
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
abstract class ScalaBaseGenerateAction(handler: ScalaCodeInsightActionHandler) extends BaseGenerateAction(handler) {

  override protected def isValidForFile(project: Project, editor: Editor, file: PsiFile): Boolean =
    file match {
      case scalaFile: ScalaFile if scalaFile.isWritable =>
        handler.isValidFor(editor, file) && isTargetClassValid(editor, scalaFile)
      case _ => false
    }

  override protected def isValidForClass(targetClass: PsiClass): Boolean = true

  override protected def getTargetClass(editor: Editor, file: PsiFile): PsiClass = {
    val offset = editor.getCaretModel.getOffset
    val element = file.findElementAt(offset)
    PsiTreeUtil.getParentOfType(element, classOf[ScTemplateDefinition])
  }

  private def isTargetClassValid(editor: Editor, file: ScalaFile): Boolean =
    getTargetClass(editor, file) match {
      case null => false
      case clazz => isValidForClass(clazz)
    }
}
