package org.jetbrains.plugins.scala.worksheet.settings
package persistent

/**
 * Marker interface to indicate that entity main purpose is to simply persist file or project default worksheet settings
 *
 * @note The interface doesn't provide any `getter` base members because file & default project settings getters have
 *       different return types.<br>
 *       For example:
 *       - [[WorksheetFilePersistentSettings.isInteractive]] can return None for a file, indicating that
 *         no specific setting value was saved for the file. Although usually settings returned values shouldn't be empty
 *         (due to [[WorksheetFileSettings.ensureSettingsArePersisted()]]), it's a possible case, for example when
 *         settings were manually changed in `.xml` files.
 *       - [[WorksheetProjectDefaultPersistentSettings.isInteractive]] always return some value cause it's State has
 *         a default fallback value known statically (see [[WorksheetProjectDefaultPersistentSettings.State]]).
 */
trait WorksheetPersistentSettings extends WorksheetSettingsUpdater

trait WorksheetSettingsUpdater {
  def setRunType(value: WorksheetExternalRunType): Unit
  def setInteractive(value: Boolean): Unit
  def setMakeBeforeRun(value: Boolean): Unit
  def setModuleName(value: String): Unit
  def setCompilerProfileName(value: String): Unit
}

object WorksheetSettingsUpdater {

  class WorksheetSettingsDelegateUpdater(delegate: WorksheetSettingsUpdater) extends WorksheetSettingsUpdater {
    override def setRunType(value: WorksheetExternalRunType): Unit = delegate.setRunType(value)
    override def setInteractive(value: Boolean): Unit = delegate.setInteractive(value)
    override def setMakeBeforeRun(value: Boolean): Unit = delegate.setMakeBeforeRun(value)
    override def setModuleName(value: String): Unit = delegate.setModuleName(value)
    override def setCompilerProfileName(value: String): Unit = delegate.setCompilerProfileName(value)
  }

}