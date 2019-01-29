package org.jetbrains.plugins.scala.lang.formatting.processors

import java.util.concurrent.ConcurrentMap

import com.intellij.application.options.CodeStyle
import com.intellij.notification.{Notification, NotificationGroup, NotificationType}
import com.intellij.openapi.application.{ApplicationManager, ModalityState}
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.{StandardFileSystems, VirtualFile}
import com.intellij.psi.{PsiFile, PsiManager}
import com.intellij.util.containers.ContainerUtil
import metaconfig.{ConfError, Configured}
import org.jetbrains.plugins.hocon.psi.HoconPsiFile
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.formatting.processors.ScalaFmtPreFormatProcessor.reportError
import org.jetbrains.plugins.scala.lang.formatting.settings.ScalaCodeStyleSettings
import org.jetbrains.sbt.language.SbtFileImpl
import org.scalafmt.config.{Config, ScalafmtConfig, ScalafmtRunner}

object ScalaFmtConfigUtil {

  val SupportedExtensions: Set[String] = Set("scala", "sc", "sbt")

  private def loadConfig(configFile: VirtualFile, project: Project): Configured[ScalafmtConfig] = {
    inReadAction {
      PsiManager.getInstance(project).findFile(configFile) match {
        case hoconFile: HoconPsiFile =>
          Config.fromHoconString(hoconFile.getText)
        case _ =>
          Configured.NotOk(ConfError.fileDoesNotExist(configFile.getCanonicalPath))
      }
    }
  }

  private def storeOrUpdate(vFile: VirtualFile, project: Project): ScalafmtConfig = {
    //TODO use of asynchronous updates is a trade-off performance vs accuracy, can this be performance-heavy?
    inWriteAction(ApplicationManager.getApplication.invokeAndWait(vFile.refresh(false, false), ModalityState.current()))
    Option(scalafmtConfigs.get(vFile)) match {
      case Some((config, stamp)) if stamp == vFile.getModificationStamp => config
      case _ =>
        loadConfig(vFile, project) match {
          case Configured.Ok(config) =>
            scalafmtConfigs.put(vFile, (config, vFile.getModificationStamp))
            config
          case Configured.NotOk(error) =>
            reportBadConfig(vFile.getCanonicalPath, project, error)
            ScalafmtConfig.intellij
        }
    }
  }

  def configFor(psi: PsiFile): ScalafmtConfig = {
    val settings = CodeStyle.getCustomSettings(psi, classOf[ScalaCodeStyleSettings])
    val project = psi.getProject
    val config = configFor(project, settings.SCALAFMT_CONFIG_PATH)
    psi match {
      case _: SbtFileImpl => config.copy(runner = ScalafmtRunner.sbt)
      case _ => config
    }
  }

  private def configFor(project: Project, configPath: String): ScalafmtConfig =
    scalaFmtConfigFile(configPath, project) match {
      case Some(custom) =>
        storeOrUpdate(custom, project)
      case _ if configPath.isEmpty =>
        //auto-detect settings
        ScalafmtConfig.intellij
      case _ =>
        reportBadConfig(configPath, project, ConfError.fileDoesNotExist(configPath))
        ScalafmtConfig.intellij
    }

  def defaultConfigurationFileName: String = ".scalafmt.conf"

  private def defaultConfigurationFile(project: Project): Option[VirtualFile] =
    project.getBaseDir.toOption
      .flatMap(baseDir => baseDir.findChild(defaultConfigurationFileName).toOption)

  def projectDefaultConfig(project: Project): Option[ScalafmtConfig] =
    defaultConfigurationFile(project)
      .map(getScalafmtProjectConfig(_, project))

  def scalaFmtConfigFile(configPath: String, project: Project): Option[VirtualFile] =
    if (configPath.isEmpty) {
      defaultConfigurationFile(project)
    } else {
      absolutePathFromConfigPath(configPath, project)
        .flatMap(path => StandardFileSystems.local.findFileByPath(path).toOption)
    }


  def isIncludedInProject(file: PsiFile): Boolean = {
    file.getVirtualFile.toOption match {
      case Some(vFile) =>
        val config = ScalaFmtConfigUtil.configFor(file)
        config.project.matcher.matches(vFile.getPath)
      case None =>
        // in-memory files can't be present in scalafmt config exclude rules, so they should always be formatted
        true
    }
  }

  private val scalafmtConfigs: ConcurrentMap[VirtualFile, (ScalafmtConfig, Long)] = ContainerUtil.createConcurrentWeakMap()

  private def reportBadConfig(path: String, project: Project, error: ConfError): Unit =
    reportError(
      s"""|Failed to load scalafmt config $path:
          |${error.msg}
          |Using default configuration instead.
       """.stripMargin, project)

  private def getScalafmtProjectConfig(vFile: VirtualFile, project: Project): ScalafmtConfig = storeOrUpdate(vFile, project)

  private def absolutePathFromConfigPath(path: String, project: Project): Option[String] = {
    Option(project.getBaseDir).map { baseDir =>
      if (path.startsWith(".")) {
        baseDir.getCanonicalPath + "/" + path
      }
      else path
    }
  }

  private val unsupportedSettingsNotificationGroup: NotificationGroup = NotificationGroup.balloonGroup("Scalafmt unsupported features")

  private def rewriteActionsNotSupported: Notification = unsupportedSettingsNotificationGroup.
    createNotification("Scalafmt rewrite rules are partially supported.", "Rewrite rules will be disabled for selection formatting.", NotificationType.WARNING, null)
}
