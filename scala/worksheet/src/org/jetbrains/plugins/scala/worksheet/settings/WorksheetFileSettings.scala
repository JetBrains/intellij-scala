package org.jetbrains.plugins.scala.worksheet.settings

import com.intellij.codeInsight.daemon.impl.DaemonCodeAnalyzerEx
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.highlighter.EditorHighlighterFactory
import com.intellij.openapi.fileEditor.{FileEditorManager, TextEditor}
import com.intellij.openapi.module.{Module, ModuleManager}
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.vfs.newvfs.FileAttribute
import com.intellij.openapi.vfs.{VirtualFile, VirtualFileWithId}
import com.intellij.psi.PsiFile
import org.jetbrains.plugins.scala.extensions.{ObjectExt, TraversableExt}
import org.jetbrains.plugins.scala.lang.psi.api.{FileDeclarationsHolder, ScFile, ScalaFile}
import org.jetbrains.plugins.scala.project._
import org.jetbrains.plugins.scala.project.settings.{ScalaCompilerConfiguration, ScalaCompilerSettingsProfile}
import org.jetbrains.plugins.scala.util.ScalaUtil
import org.jetbrains.plugins.scala.worksheet.WorksheetUtils
import org.jetbrains.plugins.scala.worksheet.processor.{FileAttributeUtilCache, WorksheetPerFileConfig}

import scala.ref.WeakReference

/**
  * User: Dmitry.Naydanov
  * Date: 14.03.18.
  */
class WorksheetFileSettings(file: PsiFile) extends WorksheetCommonSettings {

  import WorksheetFileSettings._

  override def project: Project = file.getProject

  private def getDefaultSettings = WorksheetProjectSettings(project)

  private def getSetting[T](attr: FileAttribute, orDefault: => T)
                           (implicit ev: SerializableInFileAttribute[T]): T =
    WorksheetFileSettings.getSetting(file.getVirtualFile, attr).getOrElse(orDefault)

  private def setSetting[T](attr: FileAttribute, value: T)
                           (implicit ev: SerializableInFileAttribute[T]): Unit =
    WorksheetFileSettings.setSetting(file.getVirtualFile, attr, value)

  override def getRunType: WorksheetExternalRunType = getSetting(WORKSHEET_RUN_TYPE, getDefaultSettings.getRunType)

  // TODO: not this method should be optional but WorksheetFileSettings.apply
  def getRunTypeOpt: Option[WorksheetExternalRunType] = Option(getSetting(WORKSHEET_RUN_TYPE, null: WorksheetExternalRunType))

  override def setRunType(runType: WorksheetExternalRunType): Unit = setSetting(WORKSHEET_RUN_TYPE, runType)

  override def isInteractive: Boolean = getSetting(IS_AUTORUN, getDefaultSettings.isInteractive)

  override def isMakeBeforeRun: Boolean = getSetting(IS_MAKE_BEFORE_RUN, getDefaultSettings.isMakeBeforeRun)

  override def getCompilerProfileName: String = getSetting(COMPILER_PROFILE, getDefaultSettings.getCompilerProfileName)

  override def setInteractive(value: Boolean): Unit = setSetting(IS_AUTORUN, value)

  override def setMakeBeforeRun(value: Boolean): Unit = setSetting(IS_MAKE_BEFORE_RUN, value)

  override def setCompilerProfileName(value: String): Unit = setSetting(COMPILER_PROFILE, value)

  private def isScratchFile: Boolean =
    WorksheetUtils.isScratchWorksheet(project, file.getVirtualFile)

  override def getCompilerProfile: ScalaCompilerSettingsProfile = {
    val configuration = ScalaCompilerConfiguration.instanceIn(project)

    val customProfiles = configuration.customProfiles
    val maybeCustomProfile = if (isScratchFile) {
      val name = getCompilerProfileName
      customProfiles.find(_.getName == name)
    } else {
      for {
        ScFile.VirtualFile(virtualFile) <- Some(file)
        module <- ScalaUtil.getModuleForFile(virtualFile)(project)
        profile <- customProfiles.find(_.moduleNames.contains(module.getName))
      } yield profile
    }
    maybeCustomProfile.getOrElse(configuration.defaultProfile)
  }

  override def getModuleName: String = {
    val savedModuleForFile = WorksheetFileSettings.getModuleName(file.getVirtualFile)
    savedModuleForFile.getOrElse(getDefaultSettings.getModuleName)
  }

  override def setModuleName(value: String): Unit = {
    setSetting(CP_MODULE_NAME, value)
    for {
      module <- Option(getModuleFor)
    } file.putUserData(UserDataKeys.SCALA_ATTACHED_MODULE, new WeakReference(module))

    updateEditorsHighlighters(project, file.getVirtualFile)
    DaemonCodeAnalyzerEx.getInstanceEx(project).restart(file)
  }

  override def getModuleFor: Module =
    if (isScratchFile) {
      findModuleByName
    } else {
      val fromIndex = moduleFromIndex(file.getVirtualFile, project)
      fromIndex.orElse(project.anyScalaModule).orNull
    }
}

object WorksheetFileSettings extends WorksheetPerFileConfig {

  private val IS_MAKE_BEFORE_RUN = new FileAttribute("ScalaWorksheetMakeBeforeRun", 1, true)
  private val CP_MODULE_NAME = new FileAttribute("ScalaWorksheetModuleForCp", 1, false)
  private val COMPILER_PROFILE = new FileAttribute("ScalaWorksheetCompilerProfile", 1, false)
  private val IS_AUTORUN = new FileAttribute("ScalaWorksheetAutoRun", 1, true)
  private val WORKSHEET_RUN_TYPE = new FileAttribute("ScalaWorksheetRunType", 1, false)

  def apply(file: PsiFile): WorksheetFileSettings = new WorksheetFileSettings(file)

  def isReplLight(file: FileDeclarationsHolder): Boolean = {
    file match {
      case scalaFile: ScalaFile =>
        val attrValue = FileAttributeUtilCache.readAttributeLight(WORKSHEET_RUN_TYPE, scalaFile)
        attrValue.flatMap(WorksheetExternalRunType.findRunTypeByName).exists(_.isReplRunType)
      case _ => false
    }
  }

  def isRepl(file: PsiFile): Boolean = getRunType(file).isReplRunType

  def getRunType(file: PsiFile): WorksheetExternalRunType = new WorksheetFileSettings(file).getRunType

  private def getSetting[T](vFile: VirtualFile, attr: FileAttribute)
                           (implicit ev: SerializableInFileAttribute[T]): Option[T] =
    ev.readAttribute(attr, vFile)

  private def setSetting[T](vFile: VirtualFile, attr: FileAttribute, value: T)
                           (implicit ev: SerializableInFileAttribute[T]): Unit =
    ev.writeAttribute(attr, vFile, value)

  def getModuleName(file: VirtualFile): Option[String] =
    getSetting[String](file, CP_MODULE_NAME)

  def getModuleForScratchFile(file: VirtualFile, project: Project): Option[Module] = {
    val moduleName = getModuleName(file)
    val module1 = moduleName.flatMap(ModuleManager.getInstance(project).findModuleByName(_).toOption)
    val module2 = module1.orElse(Option(WorksheetProjectSettings(project).getModuleFor))
    module2
  }

  private def moduleFromIndex(virtualFile: VirtualFile, project: Project): Option[Module] =
    virtualFile match {
      case virtualFile: VirtualFileWithId =>
        val fileIndex = ProjectFileIndex.SERVICE.getInstance(project)
        Option(fileIndex.getModuleForFile(virtualFile))
      case _ =>
        None
    }

  private def updateEditorsHighlighters(project: Project, vFile: VirtualFile): Unit = {
    val highlighter = EditorHighlighterFactory.getInstance.createEditorHighlighter(project, vFile)
    val fileEditors = FileEditorManager.getInstance(project).getAllEditors(vFile).toSeq
    val editors = fileEditors.filterByType[TextEditor].map(_.getEditor).filterByType[EditorEx]
    editors.foreach(_.setHighlighter(highlighter))
  }
}