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

class ScalaCollectShortTroubleshootingInfoAction
  extends AnAction("(Scala) Collect Troubleshooting Information Short") {

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

    s"""Scala Plugin : $scalaPluginVersion
       |IDEA Build   : $ideaBuildNumber $ideaBuildDate
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

    val message = s"Short troubleshooting summary coped to your clipboard\n$summary"
    JBPopupFactory.getInstance
      .createHtmlTextBalloonBuilder(message, MessageType.INFO, null).createBalloon()
      .show(RelativePoint.getNorthEastOf(component), Balloon.Position.atLeft)
  }

}
