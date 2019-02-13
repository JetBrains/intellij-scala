package org.jetbrains.plugins.scala.lang.formatting.processors

import java.util.concurrent.ConcurrentMap

import com.intellij.application.options.CodeStyle
import com.intellij.notification.{NotificationAction, NotificationType}
import com.intellij.openapi.application.{ApplicationManager, ModalityState}
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.{StandardFileSystems, VirtualFile}
import com.intellij.psi.{PsiFile, PsiManager}
import com.intellij.util.containers.ContainerUtil
import metaconfig.{ConfError, Configured}
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.formatting.processors.ScalaFmtPreFormatProcessor.OpenFileNotificationActon
import org.jetbrains.plugins.scala.lang.formatting.settings.ScalaCodeStyleSettings
import org.jetbrains.sbt.language.SbtFileImpl
import org.scalafmt.config.{Config, ScalafmtConfig, ScalafmtRunner}

object ScalaFmtConfigUtil {

  private val scalafmtConfigsCache: ConcurrentMap[String, (ScalafmtConfig, Long)] = ContainerUtil.newConcurrentMap()

  private def loadConfig(configFile: VirtualFile, project: Project): Configured[ScalafmtConfig] = {
    inReadAction {
      PsiManager.getInstance(project).findFile(configFile) match {
        case f: PsiFile =>
          Config.fromHoconString(f.getText)
        case null =>
          Configured.NotOk(ConfError.fileDoesNotExist(configFile.getCanonicalPath))
      }
    }
  }

  private def storeOrUpdate(vFile: VirtualFile, project: Project, failSilent: Boolean = false): ScalafmtConfig = {
    //TODO use of asynchronous updates is a trade-off performance vs accuracy, can this be performance-heavy?
    if (ApplicationManager.getApplication.isDispatchThread) {
      inWriteAction(ApplicationManager.getApplication.invokeAndWait(vFile.refresh(false, false), ModalityState.current()))
    }
    val cachedConfig = Option(scalafmtConfigsCache.get(vFile.getPath))
    cachedConfig match {
      case Some((config, stamp)) if stamp == vFile.getModificationStamp => config
      case _ =>
        loadConfig(vFile, project) match {
          case Configured.Ok(config) =>
            val contentChanged = !cachedConfig.map(_._1).contains(config)
            if(contentChanged) {
              ScalaFmtPreFormatProcessor.displayNotification(
                "scalafmt picked up new style configuration", NotificationType.INFORMATION)
            }
            scalafmtConfigsCache.put(vFile.getPath, (config, vFile.getModificationStamp))
            config
          case Configured.NotOk(error) =>
            if(!failSilent)
              reportBadConfig(vFile, project, error)
            ScalafmtConfig.intellij
        }
    }
  }

  def configFor(psi: PsiFile, failSilent: Boolean = false): ScalafmtConfig = {
    val settings = CodeStyle.getCustomSettings(psi, classOf[ScalaCodeStyleSettings])
    val project = psi.getProject
    val config = configFor(project, settings.SCALAFMT_CONFIG_PATH, failSilent)
    psi match {
      case _: SbtFileImpl => config.copy(runner = ScalafmtRunner.sbt)
      case _ => config
    }
  }

  def isFileSupported(file: VirtualFile): Boolean = isScala(file) || isSbt(file)

  private def isScala(file: VirtualFile): Boolean = file.getFileType.getName.equalsIgnoreCase("scala")
  private def isSbt(file: VirtualFile): Boolean = file.getFileType.getName.equalsIgnoreCase("sbt")

  private def configFor(project: Project, configPath: String, failSilent: Boolean): ScalafmtConfig =
    scalaFmtConfigFile(configPath, project) match {
      case Some(vFile: VirtualFile) =>
        storeOrUpdate(vFile, project, failSilent)
      case _ if configPath.isEmpty =>
        //auto-detect settings
        ScalafmtConfig.intellij
      case _ =>
        reportBadConfig(configPath, ConfError.fileDoesNotExist(configPath))
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
        val config = ScalaFmtConfigUtil.configFor(file, failSilent = true)
        config.project.matcher.matches(vFile.getPath)
      case None =>
        // in-memory files can't be present in scalafmt config exclude rules, so they should always be formatted
        true
    }
  }

  private def reportBadConfig(path: String, error: ConfError, actions: Seq[NotificationAction] = Seq()): Unit = {
    val message =
      s"""|Failed to load scalafmt config $path:
          |${error.msg}
          |Using default configuration instead.
       """.stripMargin
    ScalaFmtPreFormatProcessor.displayNotification(message, NotificationType.WARNING, actions)
  }

  private def reportBadConfig(vFile: VirtualFile, project: Project, error: ConfError): Unit = {
    // NOTE: ConfError does not provide any information about parsing error position, so setting to zero
    val action = new OpenFileNotificationActon(project, vFile, 0, "open config file")
    reportBadConfig(vFile.getCanonicalPath, error, Seq(action))
  }

  private def getScalafmtProjectConfig(vFile: VirtualFile, project: Project): ScalafmtConfig = storeOrUpdate(vFile, project)

  private[formatting] def absolutePathFromConfigPath(path: String, project: Project): Option[String] = {
    Option(project.getBaseDir).map { baseDir =>
      if (path.startsWith(".")) {
        baseDir.getCanonicalPath + "/" + path
      }
      else path
    }
  }

}
