package org.jetbrains.plugins.cbt.action

import com.intellij.ide.fileTemplates.actions.{AttributesDefaults, CreateFromTemplateActionBase}
import com.intellij.ide.fileTemplates.{FileTemplate, FileTemplateManager}
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDirectory
import org.jetbrains.plugins.scala.icons.Icons

class NewCbtBuildFile extends CreateFromTemplateActionBase("CBT Build Class",
  "Create new CBT build class", Icons.CLASS) {
  override def getTemplate(project: Project, dir: PsiDirectory): FileTemplate =
    FileTemplateManager.getDefaultInstance.getInternalTemplate("CBT Build Class")

  override def getAttributesDefaults(dataContext: DataContext): AttributesDefaults =
    new AttributesDefaults("build").withFixedName(true)
}
