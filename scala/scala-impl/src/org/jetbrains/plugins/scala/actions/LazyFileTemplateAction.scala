package org.jetbrains.plugins.scala.actions

import javax.swing.Icon

import com.intellij.ide.fileTemplates.{FileTemplate, FileTemplateManager}
import com.intellij.ide.fileTemplates.actions.CreateFromTemplateActionBase
import com.intellij.openapi.project.{DumbAware, Project}
import com.intellij.psi.PsiDirectory

/**
  * Nikolay.Tropin
  * 27-Apr-17
  */
abstract class LazyFileTemplateAction(templateName: String, val icon: Icon)
  extends CreateFromTemplateActionBase(templateName, null, icon) with DumbAware {

  private lazy val template = FileTemplateManager.getDefaultInstance.getInternalTemplate(templateName)

  override def getTemplate(project: Project, dir: PsiDirectory): FileTemplate = template
}
