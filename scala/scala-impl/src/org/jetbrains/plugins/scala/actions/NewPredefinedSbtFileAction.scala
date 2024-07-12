package org.jetbrains.plugins.scala.actions

import com.intellij.ide.IdeView
import com.intellij.ide.fileTemplates.actions.CreateFromTemplateActionBase
import com.intellij.ide.fileTemplates.{FileTemplate, FileTemplateManager}
import com.intellij.openapi.actionSystem.{CommonDataKeys, DataContext}
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.NlsActions
import com.intellij.psi.PsiDirectory
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.extensions.PsiNamedElementExt

import javax.swing.Icon

abstract class NewPredefinedSbtFileAction(@NlsActions.ActionText title: String, @NlsActions.ActionDescription description: String, icon: Icon, fileName: String) extends CreateFromTemplateActionBase(title, description, icon) {

  override def getTemplate(project: Project, dir: PsiDirectory): FileTemplate = FileTemplateManager.getDefaultInstance.getInternalTemplate(ScalaBundle.message("newclassorfile.menu.action.sbt.text"))

  override def getTargetDirectory(dataContext: DataContext, view: IdeView): PsiDirectory = {
    val directories: Array[PsiDirectory] = view.getDirectories
    directories.find(directory => directory.findFile(fileName + ".sbt") != null).exists(dir => {
      Messages.showErrorDialog(CommonDataKeys.PROJECT.getData(dataContext),
        ScalaBundle.message("error.package.already.contains.build.sbt", dir.name),
        ScalaBundle.message("error.title.package.already.contains.file", dir.name))
      return null
    }
    )
    super.getTargetDirectory(dataContext, view)
  }
}
