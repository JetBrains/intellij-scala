package org.jetbrains.plugins.scala.compiler.actions.internal

import com.intellij.ide.nls.NlsMessages
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.actionSystem.{AnAction, AnActionEvent}
import com.intellij.openapi.application.{ApplicationInfo, ApplicationNamesInfo}
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.ui.MessageType
import com.intellij.openapi.ui.popup.{Balloon, JBPopupFactory}
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.wm.WindowManager
import com.intellij.ui.awt.RelativePoint
import org.jetbrains.plugins.scala.compiler.{CompileServerLauncher, CompilerIntegrationBundle}
import org.jetbrains.plugins.scala.settings.ScalaCompileServerSettings

import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.text.SimpleDateFormat

/**
 * See also [[org.jetbrains.plugins.scala.internal.ScalaGeneralTroubleInfoCollector]]
 */
class ScalaCollectShortTroubleshootingInfoAction extends AnAction(
  CompilerIntegrationBundle.message("scala.collect.troubleshooting.information.short.action.text"),
  CompilerIntegrationBundle.message("scala.collect.troubleshooting.information.short.action.description"),
  /* icon = */ null
) {

  private val Unknown = "unknown"

  override def actionPerformed(e: AnActionEvent): Unit = {
    val summary = collectSummary(e)
    copyToClipboard(summary)
    showNotification(summary, e)
  }

  private def collectSummary(e: AnActionEvent): String = {
    val appInfo = ApplicationInfo.getInstance()
    val ideaBuildNumber = appInfo.getBuild.asString
    val ideaBuildDate = {
      val cal = appInfo.getBuildDate
      val date = if (appInfo.getBuild.isSnapshot) new SimpleDateFormat("HH:mm, ").format(cal.getTime) else ""
      date + NlsMessages.formatDateLong(cal.getTime)
    }
    val scalaPluginVersion = PluginManagerCore.getPlugins.find(_.getName == "Scala").map(_.getVersion).getOrElse("-")
    val osInfo = s"${SystemInfo.OS_NAME} (${SystemInfo.OS_VERSION}, ${SystemInfo.OS_ARCH})"

    val properties = System.getProperties
    val javaRuntime = {
      val version = properties.getProperty("java.runtime.version", properties.getProperty("java.version", Unknown))
      val osArch = properties.getProperty("os.arch", "")
      s"$version$osArch"
    }
    val javaVmName = s"${properties.getProperty("java.vm.name", Unknown)} ${properties.getProperty("java.vendor", Unknown)}"

    val projectJdkVersion = {
      val versionOption = for {
        project <- Option(e.getProject)
        jdk <- Option(ProjectRootManager.getInstance(project).getProjectSdk)
        version <- Option(jdk.getVersionString)
      } yield version
      versionOption.getOrElse("<unknown>")
    }

    val compileServerJdkVersion = Option(e.getProject).flatMap { project =>
      val settings = ScalaCompileServerSettings.getInstance
      if (!settings.COMPILE_SERVER_ENABLED)
        Some("<server disabled>")
      else if (settings.USE_DEFAULT_SDK)
        Option(CompileServerLauncher.defaultSdk(project).getVersionString)
      else
        for {
          sdk <- Option(ProjectJdkTable.getInstance.findJdk(settings.COMPILE_SERVER_SDK))
          version <- Option(sdk.getVersionString)
        } yield version
    }.getOrElse("<unknown>")

    val appName = appInfo.getFullApplicationName
    val edition = ApplicationNamesInfo.getInstance.getEditionName
    //User-readable IDEA name+version
    //Example: IntelliJ IDEA 2023.1 EAP (Ultimate Edition)
    val appNameWithEdition = if (edition == null) appName else s"$appName ($edition)"

    s"""Scala Plugin : $scalaPluginVersion
       |$appNameWithEdition
       |Build        : $ideaBuildNumber $ideaBuildDate
       |JRE          : $javaRuntime ($javaVmName)
       |OS           : $osInfo
       |Project JDK  : $projectJdkVersion
       |Server JDK   : $compileServerJdkVersion
       |""".stripMargin
  }

  private def copyToClipboard(summary: String): Unit = {
    val clipboard = Toolkit.getDefaultToolkit.getSystemClipboard
    clipboard.setContents(new StringSelection(summary), null)
  }

  private def showNotification(summary: String, e: AnActionEvent): Unit = {
    val project = e.getProject
    if (project == null) return

    val component = WindowManager.getInstance.getFrame(project).getRootPane
    if (component == null) return

    val message = CompilerIntegrationBundle.message("short.troubleshooting.summary.copied.to.your.clipboard.with.summary", summary)
    JBPopupFactory.getInstance
      .createHtmlTextBalloonBuilder(message, MessageType.INFO, null).createBalloon()
      .show(RelativePoint.getNorthEastOf(component), Balloon.Position.atLeft)
  }

}
