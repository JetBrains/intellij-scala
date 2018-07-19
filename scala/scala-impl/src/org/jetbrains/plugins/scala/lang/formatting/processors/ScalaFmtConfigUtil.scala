package org.jetbrains.plugins.scala.lang.formatting.processors

import java.util.concurrent.ConcurrentMap

import com.intellij.application.options.CodeStyle
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.{StandardFileSystems, VirtualFile}
import com.intellij.psi.{PsiFile, PsiManager}
import com.intellij.util.containers.ContainerUtil
import org.jetbrains.plugins.hocon.psi.HoconPsiFile
import org.jetbrains.plugins.scala.extensions.inReadAction
import org.jetbrains.plugins.scala.lang.formatting.processors.ScalaFmtPreFormatProcessor.reportError
import org.jetbrains.plugins.scala.lang.formatting.settings.ScalaCodeStyleSettings
import org.jetbrains.sbt.language.SbtFileImpl
import org.scalafmt.config.{Config, ScalafmtConfig, ScalafmtRunner}

object ScalaFmtConfigUtil {

  def loadConfig(configFile: VirtualFile, project: Project): Option[ScalafmtConfig] = {
    inReadAction{PsiManager.getInstance(project).findFile(configFile) match {
      case hoconFile: HoconPsiFile =>
        Config.fromHoconString(hoconFile.getText).toEither.toOption
      case _ => None
    }}
  }

  def storeOrUpdate(vFile: VirtualFile, project: Project): ScalafmtConfig = {
    vFile.refresh(false, false) //TODO use of asynchronous updates is a trade-off performance vs accuracy, can this be performance-heavy?
    Option(scalafmtConfigs.get(vFile)) match {
      case Some((config, stamp)) if stamp == vFile.getModificationStamp => config
      case _ =>
        loadConfig(vFile,project).map{ config =>
          scalafmtConfigs.put(vFile, (config, vFile.getModificationStamp))
          config
        }.getOrElse {
          reportBadConfig(vFile.getCanonicalPath, project)
          ScalafmtConfig.intellij
        }
    }
  }

  def configFor(psi: PsiFile): ScalafmtConfig = {
    val settings = CodeStyle.getCustomSettings(psi, classOf[ScalaCodeStyleSettings])
    val project = psi.getProject
    val config = if (settings.SCALAFMT_CONFIG_PATH.nonEmpty) {
      scalaFmtConfigFile(settings, project) match {
        case Some(custom) => storeOrUpdate(custom, project)
        case _ =>
          reportBadConfig(settings.SCALAFMT_CONFIG_PATH, project)
          ScalafmtConfig.intellij
      }
    } else {
      //auto-detect settings
      projectDefaultConfig(project).getOrElse(ScalafmtConfig.intellij)
    }
    psi match {
      case _: SbtFileImpl => config.copy(runner = ScalafmtRunner.sbt)
      case _ => config
    }
  }

  def projectDefaultConfig(project: Project): Option[ScalafmtConfig] = Option(project.getBaseDir.findChild(".scalafmt.conf")).
    map(getScalafmtProjectConfig(_, project))

  def scalaFmtConfigFile(settings: ScalaCodeStyleSettings, project: Project): Option[VirtualFile] =
    Option(StandardFileSystems.local.findFileByPath(absolutePathFromConfigPath(settings.SCALAFMT_CONFIG_PATH, project)))

  private val scalafmtConfigs: ConcurrentMap[VirtualFile, (ScalafmtConfig, Long)] = ContainerUtil.createConcurrentWeakMap()

  private def reportBadConfig(path: String, project: Project): Unit =
    reportError("Failed to load scalafmt config " + path + ", using default configuration instead", project)

  private def getScalafmtProjectConfig(vFile: VirtualFile, project: Project): ScalafmtConfig = storeOrUpdate(vFile, project)

  private def absolutePathFromConfigPath(path: String, project: Project): String = {
    if (path.startsWith(".")) {
      project.getBaseDir.getCanonicalPath + "/" + path
    } else path
  }
}
