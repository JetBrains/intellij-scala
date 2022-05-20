package org.jetbrains.plugins.scala.actions

import com.intellij.ide.fileTemplates.actions.CreateFromTemplateActionBase
import com.intellij.ide.fileTemplates.{FileTemplate, FileTemplateManager}
import com.intellij.openapi.project.{DumbAware, Project}
import com.intellij.psi.PsiDirectory
import org.jetbrains.annotations.Nls

import javax.swing.Icon

abstract class LazyFileTemplateAction(
  templateName: String, // defined in plugin xml file with <internalFileTemplate ... /> tag
  @Nls title: String,
  @Nls description: String,
  val icon: Icon
) extends CreateFromTemplateActionBase(
  title,
  description,
  icon
) with DumbAware {

  private lazy val template = FileTemplateManager.getDefaultInstance.getInternalTemplate(templateName)

  override def getTemplate(project: Project, dir: PsiDirectory): FileTemplate = template
}
