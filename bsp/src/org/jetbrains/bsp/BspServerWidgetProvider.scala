package org.jetbrains.bsp

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.extensions.LoadingOrder
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.IconLoader
import com.intellij.openapi.wm.{StatusBarWidget, StatusBarWidgetProvider}
import com.intellij.util.messages.Topic

import javax.swing.Icon
import scala.annotation.nowarn

/**
 * This class still needs to extend deprecated [[com.intellij.openapi.wm.StatusBarWidgetProvider]]
 * due to missing anchor API in [[com.intellij.openapi.wm.StatusBarWidgetFactory]].
 */
@nowarn("msg=StatusBarWidgetProvider")
private final class BspServerWidgetProvider extends StatusBarWidgetProvider {

  override def getWidget(project: Project): StatusBarWidget = {
    if (BspUtil.isBspProject(project)) new BspServerWidget()
    else null
  }

  override def getAnchor: String = LoadingOrder.before("Position").toString
}

private object BspServerWidgetProvider {
  val ID: String = "BSP Manager"

  val IconRunning: Icon = Icons.BSP
  val IconStopped: Icon = IconLoader.getDisabledIcon(IconRunning)
  val logger: Logger = Logger.getInstance(classOf[BspServerWidgetProvider])

  trait UpdateWidgetListener {
    def updateWidget(): Unit
  }

  val Topic: Topic[UpdateWidgetListener] =
    new Topic("Bsp server widget update topic", classOf[UpdateWidgetListener])
}
