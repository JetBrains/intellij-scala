package org.jetbrains.plugins.scala.util

import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.scala.project.settings.{ScalaCompilerSettings, ScalaCompilerSettingsProfile}
import org.jetbrains.plugins.scala.settings.ScalaProjectSettings

object CommonScalaRevertableChanges {

  def withModifiedScalaProjectSettings[T](
    project: Project,
    get: ScalaProjectSettings => T,
    set: (ScalaProjectSettings, T) => Unit,
    value: T
  ): RevertableChange = new RevertableChange {
    private def instance: ScalaProjectSettings = ScalaProjectSettings.getInstance(project)

    private var before: Option[T] = None

    override def applyChange(): Unit = {
      before = Some(get(instance))
      set(instance, value)
    }

    override def revertChange(): Unit =
      before.foreach(set(instance, _))
  }

  def withCompilerSettingsModified(
    module: Module,
    getModifiedCopy: ScalaCompilerSettings => ScalaCompilerSettings
  ): RevertableChange = new RevertableChange {
    private lazy val profile = ScalaCompilerSettingsProfile.forModule(module)
    private lazy val oldSettings = profile.getSettings

    override def applyChange(): Unit = {
      val newSettings = getModifiedCopy(oldSettings)
      profile.setSettings(newSettings)
    }

    override def revertChange(): Unit = {
      profile.setSettings(oldSettings)
    }
  }
}
