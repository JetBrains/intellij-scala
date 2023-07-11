package org.jetbrains.plugins.scala.components

import com.intellij.openapi.project.DumbService.DumbModeListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.impl.status.widget.StatusBarWidgetsManager
import com.intellij.openapi.wm.{StatusBar, StatusBarWidget, StatusBarWidgetFactory}
import com.intellij.util.messages.Topic
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.project.ProjectExt

import scala.jdk.CollectionConverters._

private final class TypeAwareWidgetFactory extends StatusBarWidgetFactory {
  override def getId: String = TypeAwareWidgetFactory.ID

  override def getDisplayName: String = ScalaBundle.message("scala.type.aware.highlighting.indicator")

  override def isAvailable(project: Project): Boolean = project.isOpen && project.hasScala

  override def createWidget(project: Project): StatusBarWidget = new TypeAwareWidget(project, this)

  override def disposeWidget(widget: StatusBarWidget): Unit = {
    Disposer.dispose(widget)
  }

  override def canBeEnabledOn(statusBar: StatusBar): Boolean = isAvailable(statusBar.getProject)
}

private[scala] object TypeAwareWidgetFactory {
  private[components] val ID: String = "TypeAwareHighlighting"

  trait UpdateListener {
    def updateWidget(): Unit
  }

  val Topic: Topic[UpdateListener] = new Topic("TypeAwareHighlightingWidget update", classOf[UpdateListener])

  final class Listener(project: Project) extends DumbModeListener {
    override def exitDumbMode(): Unit = {
      val service = project.getService(classOf[StatusBarWidgetsManager])
      service.getWidgetFactories.asScala.find(_.getId == ID).foreach(service.updateWidget)
    }
  }
}
