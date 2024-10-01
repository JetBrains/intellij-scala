package org.jetbrains.plugins.scala.actions

import com.intellij.ide.actions.CreateFromTemplateAction.moveCaretAfterNameIdentifier
import com.intellij.ide.fileTemplates.actions.AttributesDefaults
import com.intellij.ide.fileTemplates.ui.CreateFromTemplateDialog
import com.intellij.ide.fileTemplates.{FileTemplate, FileTemplateManager}
import com.intellij.openapi.actionSystem._
import com.intellij.openapi.module.Module
import com.intellij.psi.{JavaDirectoryService, PsiDirectory, PsiElement}
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.extensions.{ObjectExt, PsiElementExt}
import org.jetbrains.plugins.scala.icons.Icons
import org.jetbrains.plugins.scala.project._

class NewPackageObjectAction extends LazyFileTemplateAction(
  "Package Object",
  ScalaBundle.message("new.packageobject.menu.action.text"),
  ScalaBundle.message("new.packageobject.menu.action.description"),
  Icons.PACKAGE_OBJECT
) {

  override def update(e: AnActionEvent): Unit = {
    super.update(e)

    e.getPresentation.setIcon(icon)

    val hasPackage = Option(LangDataKeys.IDE_VIEW.getData(e.getDataContext))
            .flatMap(_.getDirectories.headOption)
            .flatMap(dir => Option(JavaDirectoryService.getInstance.getPackage(dir)))
            .map(_.getQualifiedName)
            .exists(_.nonEmpty)

    val module: Module = e.getDataContext.getData(PlatformCoreDataKeys.MODULE)
    val isEnabled: Boolean = Option(module).exists(_.hasScala)

    e.getPresentation.setEnabled(hasPackage && isEnabled)
    e.getPresentation.setVisible(hasPackage && isEnabled)
  }

  override def getActionUpdateThread: ActionUpdateThread = ActionUpdateThread.BGT

  override def getAttributesDefaults(dataContext: DataContext): AttributesDefaults =
    new AttributesDefaults("package").withFixedName(true)

  override def getReplacedAction(selectedTemplate: FileTemplate): AnAction = event => {
    val dataContext = event.getDataContext

    for {
      view           <- LangDataKeys.IDE_VIEW.getData(dataContext).toOption
      dir            <- getTargetDirectory(dataContext, view).toOption
      defaults        = getAttributesDefaults(dataContext)
      createdElement <- createElement(selectedTemplate, dir, defaults)
      _               = view.selectElement(createdElement)
      file           <- createdElement.containingScalaFile
      obj            <- file.typeDefinitions.headOption
    } moveCaretAfterNameIdentifier(obj)
  }

  private def createElement(
    selectedTemplate: FileTemplate,
    directory: PsiDirectory,
    defaults: AttributesDefaults
  ): Option[PsiElement] = {
    val project = directory.getProject
    FileTemplateManager.getInstance(project).addRecentName(selectedTemplate.getName)
    val properties = if (defaults != null) defaults.getDefaultProperties else null
    val dialog = new CreateFromTemplateDialog(project, directory, selectedTemplate, defaults, properties)
    val createdElement = dialog.create()
    if (createdElement != null) {
      elementCreated(dialog, createdElement)
      Some(createdElement)
    } else None
  }
}

object NewPackageObjectAction {
  val ID: String = "Scala.NewPackageObject"
}
