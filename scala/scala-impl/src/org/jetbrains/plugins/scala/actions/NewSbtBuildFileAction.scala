package org.jetbrains.plugins.scala.actions

import com.intellij.ide.IdeView
import com.intellij.ide.fileTemplates.actions.{AttributesDefaults, CreateFromTemplateActionBase}
import com.intellij.ide.fileTemplates.{FileTemplate, FileTemplateManager}
import com.intellij.openapi.actionSystem.{CommonDataKeys, DataContext}
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.psi.PsiDirectory
import org.jetbrains.plugins.scala.extensions.PsiNamedElementExt
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.sbt.icons.Icons

final class NewSbtBuildFileAction extends CreateFromTemplateActionBase(
  ScalaBundle.message("newclassorfile.menu.action.build.sbt.text"),
  ScalaBundle.message("newclassorfile.menu.action.sbt.description"),
  Icons.SBT_FILE
) {

  override def getTemplate(project: Project, dir: PsiDirectory): FileTemplate = FileTemplateManager.getDefaultInstance.getInternalTemplate(ScalaBundle.message("newclassorfile.menu.action.sbt.text"))

  protected override def getAttributesDefaults(dataContext: DataContext): AttributesDefaults = new AttributesDefaults(ScalaBundle.message("newclassorfile.menu.action.build.sbt.defaultName")).withFixedName(true)

  override def getTargetDirectory(dataContext: DataContext, view: IdeView): PsiDirectory = {
    val directories: Array[PsiDirectory] = view.getDirectories
    for (directory <- directories) {
      if (directory.findFile(ScalaBundle.message("newclassorfile.menu.action.build.sbt.defaultName") + ".sbt") != null) {
        Messages.showErrorDialog(CommonDataKeys.PROJECT.getData(dataContext),
          ScalaBundle.message("error.package.already.contains.build.sbt", directory.name),
          "Cannot Create File")
        return null
      }
    }
    super.getTargetDirectory(dataContext, view)
  }
}
