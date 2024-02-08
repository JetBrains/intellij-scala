package org.jetbrains.plugins.scala.worksheet.settings

import com.intellij.openapi.module.{Module, ModuleManager}
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import com.intellij.util.SlowOperations
import org.jetbrains.plugins.scala.project.ProjectExt
import org.jetbrains.plugins.scala.project.settings.{ScalaCompilerConfiguration, ScalaCompilerSettingsProfile}
import org.jetbrains.plugins.scala.util.ScalaUtil
import org.jetbrains.plugins.scala.worksheet.WorksheetUtils
import org.jetbrains.plugins.scala.worksheet.settings.persistent.{WorksheetFilePersistentSettings, WorksheetProjectDefaultPersistentSettings}

import scala.util.Using

/**
 * The class represent worksheet settings which are actually used by the worksheet
 * (when evaluating worksheet, when highlighting, inspecting, etc...).<br>
 * Unlike [[WorksheetFilePersistentSettings]], settings returned value types represent actual types that are
 * used in other subsystems. For simple primitive types (e.g. for Boolean) there is no any difference.
 * But it matters for business entities, for example:
 *
 *  - `getModule` returns the actual module of type Module, not just saved module name.<br>
 *    Returned module can have a different name from the saved module name.<br>
 *    For example, if a worksheet is physically in some module,
 *    it ignores saved module name and returns module to which the file belongs).
 *    This can be helpful: if some module was renamed, we don't use old saved module name.
 *    Practically it means that saved module name by scratch files worksheets.
 *
 *  - `getCompilerProfile` returns the actual compiler profile, not saved profile name<br>
 *
 * @see [[WorksheetFileSettings]]
 * @see [[persistent.WorksheetPersistentSettings]]
 */
final class WorksheetFileSettings private(
  project: Project,
  file: VirtualFile
) {

  private def filePersistentSettings =
    WorksheetFilePersistentSettings(file)
  private def defaultPersistentSettings =
    WorksheetProjectDefaultPersistentSettings(project)

  private def persistedSetting[T](
    getForFile: WorksheetFilePersistentSettings => Option[T],
    getForProjectDefault: WorksheetProjectDefaultPersistentSettings => T
  ): T = {
    val res1 = getForFile(filePersistentSettings)
    val res2 = res1.getOrElse(getForProjectDefault(defaultPersistentSettings))
    res2
  }

  private def persistedSetting[T](
    getForFile: WorksheetFilePersistentSettings => Option[T],
    getProjectDefault: WorksheetProjectDefaultPersistentSettings => Option[T]
  ): Option[T] = {
    val res1 = getForFile(filePersistentSettings)
    val res2 = res1.orElse(getProjectDefault(defaultPersistentSettings))
    res2
  }

  def isRepl: Boolean = getRunType.isReplRunType

  def getRunType: WorksheetExternalRunType = persistedSetting(_.getRunType, _.getRunType)
  def isInteractive: Boolean = persistedSetting(_.isInteractive, _.isInteractive)
  def isMakeBeforeRun: Boolean = persistedSetting(_.isMakeBeforeRun, _.isMakeBeforeRun)

  def getModuleName: Option[String] = getModule.map(_.getName)

  def getModule: Option[Module] = {
    // We don't allow changing worksheet module if it is already located in some module (non-scratch-files)
    val fixedModule =
      if (!WorksheetUtils.isScratchWorksheet(project, file)) {
        val fromIndex = ScalaUtil.getModuleForFile(file)(project)
        fromIndex
      } else {
        None
      }
    val maybeModule1 = fixedModule.orElse(moduleFromPersistedSettings)
    val maybeModule2 = maybeModule1.orElse(project.anyScalaModule)
    maybeModule2
  }

  private def moduleFromPersistedSettings: Option[Module] = {
    val moduleName = persistedSetting(_.getModuleName, _.getModuleName)
    moduleName.flatMap(findModule)
  }

  private def findModule(moduleName: String): Option[Module] =
    Option(ModuleManager.getInstance(project).findModuleByName(moduleName))

  def getCompilerProfileName: String = getCompilerProfile.getName

  def getCompilerProfile: ScalaCompilerSettingsProfile = {
    val profile1 = profileFromPersistedSettings
    val profile2 = profile1.orElse(profileFromModule)
    val profile3 = profile2.getOrElse(ScalaCompilerConfiguration(project).defaultProfile)
    profile3
  }

  private def profileFromModule: Option[ScalaCompilerSettingsProfile] = {
    val maybeModule = getModule
    for {
      module  <- maybeModule
      profile = ScalaCompilerConfiguration(project).getProfileForModule(module)
    } yield profile
  }

  private def profileFromPersistedSettings: Option[ScalaCompilerSettingsProfile] = {
    val maybeProfileName = persistedSetting(_.getCompilerProfileName, _.getCompilerProfileName)
    for {
      profileName <- maybeProfileName
      profile     <- ScalaCompilerConfiguration(project).findByProfileName(profileName)
    } yield profile
  }

  /**
   * Ensures that current effective settings are persisted.<br>
   * Because of this, changes in project default settings don't change already-created worksheets settings.<br>
   * (This works exactly the same way as in Run Configuration templates)
   */
  def ensureSettingsArePersisted(): Unit = {
    filePersistentSettings.setRunType(getRunType)
    filePersistentSettings.setInteractive(isInteractive)
    filePersistentSettings.setMakeBeforeRun(isMakeBeforeRun)
    Using.resource(SlowOperations.knownIssue("SCL-22095, SCL-22097")) { _ =>
      getModuleName.foreach(filePersistentSettings.setModuleName)
    }
    filePersistentSettings.setCompilerProfileName(getCompilerProfileName)
  }
}

object WorksheetFileSettings {
  def apply(project: Project, file: VirtualFile): WorksheetFileSettings = new WorksheetFileSettings(project, file)
  def apply(psiFile: PsiFile): WorksheetFileSettings = new WorksheetFileSettings(psiFile.getProject, psiFile.getVirtualFile)
}