package org.jetbrains.plugins.scala.actions

import com.intellij.ide.fileTemplates.actions.AttributesDefaults
import com.intellij.openapi.actionSystem._
import com.intellij.openapi.module.Module
import com.intellij.psi.JavaDirectoryService
import org.jetbrains.plugins.scala.ScalaBundle
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
            .exists(!_.isEmpty)

    val module: Module = e.getDataContext.getData(PlatformCoreDataKeys.MODULE.getName).asInstanceOf[Module]
    val isEnabled: Boolean = Option(module).exists(_.hasScala)

    e.getPresentation.setEnabled(hasPackage && isEnabled)
    e.getPresentation.setVisible(hasPackage && isEnabled)
  }

  override def getAttributesDefaults(dataContext: DataContext): AttributesDefaults = {
    new AttributesDefaults("package").withFixedName(true)
  }
}