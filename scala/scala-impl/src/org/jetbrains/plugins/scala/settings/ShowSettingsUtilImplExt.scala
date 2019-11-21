package org.jetbrains.plugins.scala.settings

import java.util.Collections

import com.intellij.openapi.options.{Configurable, ConfigurableGroup}
import com.intellij.openapi.options.ex.{ConfigurableExtensionPointUtil, ConfigurableVisitor, ConfigurableWrapper}
import com.intellij.openapi.options.newEditor.SettingsDialogFactory
import com.intellij.openapi.project.{Project, ProjectManager}

/**
 * see [[com.intellij.ide.actions.ShowSettingsUtilImpl]]
 */
object ShowSettingsUtilImplExt {

  /**
   * see [[com.intellij.ide.actions.ShowSettingsUtilImpl.showSettingsDialog(project: Project, idToSelect: String, filter: String)]]
   */
  def showSettingsDialog(project: Project, configurableClass: Class[_ <: Configurable], filter: String): Unit = {
    val group = ConfigurableExtensionPointUtil.getConfigurableGroup(project, true)
    val visitor: ConfigurableVisitor = (c: Configurable) => ConfigurableWrapper.cast(configurableClass, c) != null
    val config = visitor.find(group)
    val dialog = SettingsDialogFactory.getInstance.create(getProject(project), Collections.singletonList(group), config, filter)
    dialog.show()
  }

  private def getProject(project: Project): Project =
    if (project != null) project
    else ProjectManager.getInstance.getDefaultProject
}
