package org.jetbrains.plugins.scala
package codeInsight
package generation
package actions

import com.intellij.codeInsight.generation.actions.BaseGenerateAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi._
import org.jetbrains.annotations.Nls
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTemplateDefinition

abstract class ScalaBaseGenerateAction(handler: ScalaCodeInsightActionHandler,
                                       @Nls actionText: String,
                                       @Nls actionDescription: String) extends BaseGenerateAction(handler) {
  locally {
    val presentation = getTemplatePresentation
    presentation.setText(actionText)
    presentation.setDescription(actionDescription)
  }

  override protected def isValidForFile(project: Project, editor: Editor, file: PsiFile): Boolean =
    file match {
      case scalaFile: ScalaFile if scalaFile.isWritable =>
        handler.isValidFor(editor, file) && targetClass(editor, scalaFile).exists(isValidForClass)
      case _ => false
    }

  override protected def isValidForClass(targetClass: PsiClass): Boolean = true

  override protected def getTargetClass(editor: Editor, file: PsiFile): PsiClass =
    targetClass(editor, file).orNull

  private def targetClass(implicit editor: Editor, file: PsiFile) =
    elementOfTypeAtCaret(classOf[ScTemplateDefinition])
}

