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

    /**
     * [[TypeAwareWidgetFactory#isAvailable]] is implemented in terms of `project.hasScala`. This method always returns
     * false on a brand new project which hasn't had the Scala SDK registered to it yet. The platform calls
     * `isAvailable` much earlier than the registration of the Scala SDK, and thus, the Type Aware Highlighting widget
     * is not created for new Scala projects on their initial load.
     *
     * In general, we want to add or remove the Type Aware Highlighting widget whenever a project gains the Scala SDK or
     * loses it, respectively. We can be sure that we can query this fact after indexing is done (when exiting dumb
     * mode). The javadoc of [[StatusBarWidgetFactory#isAvailable]] suggests that
     * [[StatusBarWidgetsManager#updateWidget]] should be called manually whenever the availability of a widget changes.
     *
     * Our listener calls this method for the current project when the IDE exits dumb mode. `updateWidget` calls
     * `isAvailable`, which decides whether the Type Aware Highlighting widget should be shown or removed for the given
     * project, based on whether it is a Scala project.
     */
    override def exitDumbMode(): Unit = {
      val service = project.getService(classOf[StatusBarWidgetsManager])
      service.getWidgetFactories.asScala.find(_.getId == ID).foreach(service.updateWidget)
    }
  }
}
