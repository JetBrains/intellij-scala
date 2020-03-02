package org.jetbrains.plugins.scala.actions.internal

import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.text.SimpleDateFormat

import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.actionSystem.{AnAction, AnActionEvent}
import com.intellij.openapi.application.ex.ApplicationInfoEx
import com.intellij.openapi.ui.MessageType
import com.intellij.openapi.ui.popup.{Balloon, JBPopupFactory}
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.wm.WindowManager
import com.intellij.ui.awt.RelativePoint
import com.intellij.util.text.DateFormatUtil
import org.jetbrains.plugins.scala.ScalaBundle

class ScalaCollectShortTroubleshootingInfoAction extends AnAction(
  ScalaBundle.message("scala.collect.troubleshooting.information.short.action.text"),
  ScalaBundle.message("scala.collect.troubleshooting.information.short.action.description"),
  /* icon = */ null
) {

  private val Unknown = "unknown"

  override def actionPerformed(e: AnActionEvent): Unit = {
    val summary = collectSummary
    copyToClipboard(summary)
    showNotification(summary, e)
  }

  private def collectSummary: String = {
    val appInfo = ApplicationInfoEx.getInstanceEx
    val ideaBuildNumber = appInfo.getBuild.asString
    val ideaBuildDate = {
      val cal = appInfo.getBuildDate
      val date = if (appInfo.getBuild.isSnapshot) new SimpleDateFormat("HH:mm, ").format(cal.getTime) else ""
      date + DateFormatUtil.formatAboutDialogDate(cal.getTime)
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

    s"""Scala Plugin : $scalaPluginVersion
       |IDEA Build   : $ideaBuildNumber $ideaBuildDate
       |JRE          : $javaRuntime ($javaVmName)
       |OS           : $osInfo
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

    val message = ScalaBundle.message("short.troubleshooting.summary.copied.to.your.clipboard.with.summary", summary)
    JBPopupFactory.getInstance
      .createHtmlTextBalloonBuilder(message, MessageType.INFO, null).createBalloon()
      .show(RelativePoint.getNorthEastOf(component), Balloon.Position.atLeft)
  }

}
