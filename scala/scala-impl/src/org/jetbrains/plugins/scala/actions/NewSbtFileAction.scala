package org.jetbrains.plugins.scala.actions

import com.intellij.ide.fileTemplates.actions.CreateFromTemplateActionBase
import com.intellij.ide.fileTemplates.{FileTemplate, FileTemplateManager}
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDirectory
import org.jetbrains.sbt.icons.Icons
import org.jetbrains.plugins.scala.ScalaBundle

final class NewSbtFileAction extends CreateFromTemplateActionBase(
  ScalaBundle.message("newclassorfile.menu.action.sbt.text"),
  ScalaBundle.message("newclassorfile.menu.action.sbt.description"),
  Icons.SBT_FILE
) {

  override def getTemplate(project: Project, dir: PsiDirectory): FileTemplate = FileTemplateManager.getDefaultInstance.getInternalTemplate("Sbt File")

}
