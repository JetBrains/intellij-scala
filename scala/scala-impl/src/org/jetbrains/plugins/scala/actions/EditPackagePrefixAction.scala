package org.jetbrains.plugins.scala.actions

import com.intellij.openapi.actionSystem._
import com.intellij.openapi.roots.ModuleRootManager
import org.jetbrains.jps.model.java.JavaModuleSourceRootTypes
import org.jetbrains.plugins.scala.{ScalaBundle, extensions}

import javax.swing.JComponent

class EditPackagePrefixAction extends AnAction(ScalaBundle.message("edit.package.prefix")) {
  override def actionPerformed(e: AnActionEvent): Unit = {
    val modifiableModel = {
      val module = PlatformCoreDataKeys.MODULE.getData(e.getDataContext)
      ModuleRootManager.getInstance(module).getModifiableModel
    }

    val dialog = {
      val file = CommonDataKeys.VIRTUAL_FILE.getData(e.getDataContext)
      val sourceFolder = modifiableModel.getContentEntries.flatMap(_.getSourceFolders).find(_.getFile == file)
      val properties = sourceFolder.get.getJpsElement.getProperties(JavaModuleSourceRootTypes.SOURCES)
      new SourceRootPropertiesDialog(PlatformCoreDataKeys.CONTEXT_COMPONENT.getData(e.getDataContext).asInstanceOf[JComponent], properties)
    }

    if (dialog.showAndGet) {
      extensions.inWriteAction {
        modifiableModel.commit()
      }
    }
  }

  override def update(e: AnActionEvent): Unit = {
    super.update(e)

    val isSourceRoot =
      Option(PlatformCoreDataKeys.MODULE.getData(e.getDataContext))
        .flatMap(module => Option(CommonDataKeys.VIRTUAL_FILE.getData(e.getDataContext))
          .map(file => ModuleRootManager.getInstance(module).getSourceRoots.contains(file)))
        .getOrElse(false)

    e.getPresentation.setEnabledAndVisible(isSourceRoot)
  }

  override def getActionUpdateThread: ActionUpdateThread = ActionUpdateThread.BGT
}
