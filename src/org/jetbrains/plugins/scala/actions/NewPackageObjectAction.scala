package org.jetbrains.plugins.scala
package actions

import com.intellij.openapi.project.DumbAware
import icons.Icons
import com.intellij.ide.fileTemplates.actions.{AttributesDefaults, CreateFromTemplateAction}
import com.intellij.ide.fileTemplates.FileTemplateManager
import com.intellij.openapi.actionSystem._
import com.intellij.psi.JavaDirectoryService
import com.intellij.openapi.module.Module
import project._

/**
 * Pavel Fatin
 */

class NewPackageObjectAction
        extends CreateFromTemplateAction(FileTemplateManager.getInstance().getInternalTemplate("Package Object"))
        with DumbAware {

  override def update(e: AnActionEvent) {
    super.update(e)

    e.getPresentation.setIcon(Icons.PACKAGE_OBJECT)

    val hasPackage = Option(LangDataKeys.IDE_VIEW.getData(e.getDataContext))
            .flatMap(_.getDirectories.headOption)
            .flatMap(dir => Option(JavaDirectoryService.getInstance.getPackage(dir)))
            .map(_.getQualifiedName)
            .exists(!_.isEmpty)

    val module: Module = e.getDataContext.getData(LangDataKeys.MODULE.getName).asInstanceOf[Module]
    val isEnabled: Boolean = Option(module).exists(_.hasScala)

    e.getPresentation.setEnabled(hasPackage && isEnabled)
    e.getPresentation.setVisible(hasPackage && isEnabled)
  }

  override def getAttributesDefaults(dataContext: DataContext) = {
    new AttributesDefaults("package").withFixedName(true)
  }
}