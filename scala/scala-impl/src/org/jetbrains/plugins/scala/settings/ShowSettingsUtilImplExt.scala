package org.jetbrains.plugins.scala.settings

import com.intellij.openapi.options.ex.{ConfigurableExtensionPointUtil, ConfigurableVisitor, ConfigurableWrapper}
import com.intellij.openapi.options.newEditor.SettingsDialogFactory
import com.intellij.openapi.options.{Configurable, SearchableConfigurable}
import com.intellij.openapi.project.{Project, ProjectManager}
import org.jetbrains.plugins.scala.lang.formatting.settings.ScalaLanguageCodeStyleSettingsProvider

import java.util.Collections

/**
 * see [[com.intellij.ide.actions.ShowSettingsUtilImpl]]
 */
object ShowSettingsUtilImplExt {

  /**
   * see [[com.intellij.ide.actions.ShowSettingsUtilImpl.showSettingsDialog(project: Project, idToSelect: String, filter: String)]]
   */
  def showSettingsDialog(project: Project, configurableClass: Class[_ <: Configurable], filter: String): Unit = {
    val visitor: ConfigurableVisitor = (c: Configurable) => ConfigurableWrapper.cast(configurableClass, c) != null
    showSettingsDialogImpl(project, visitor, filter)
  }

  def showScalaCodeStyleSettingsDialog(project: Project, filter: String): Unit = {
    val visitor: ConfigurableVisitor = {
      case sc: SearchableConfigurable => sc.getOriginalClass == classOf[ScalaLanguageCodeStyleSettingsProvider]
      case _                          => false
    }
    showSettingsDialogImpl(project, visitor, filter)
  }

  private def showSettingsDialogImpl(project: Project, visitor: ConfigurableVisitor, filter: String): Unit = {
    val group = ConfigurableExtensionPointUtil.getConfigurableGroup(project, true)
    val config = visitor.find(group)
    val dialog = SettingsDialogFactory.getInstance.create(getProject(project), Collections.singletonList(group), config, filter)
    dialog.show()
  }

  private def getProject(project: Project): Project =
    if (project != null) project
    else ProjectManager.getInstance.getDefaultProject
}
