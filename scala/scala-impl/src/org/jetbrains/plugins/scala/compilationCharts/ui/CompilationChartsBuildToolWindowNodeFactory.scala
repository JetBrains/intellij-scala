package org.jetbrains.plugins.scala.compilationCharts.ui

import com.intellij.build.events.impl.AbstractBuildEvent
import com.intellij.build.events.{BuildEvent, BuildEventPresentationData, PresentableBuildEvent, StartBuildEvent}
import com.intellij.build.{BuildProgressListener, BuildViewManager, DefaultBuildDescriptor}
import com.intellij.concurrency.JobScheduler
import com.intellij.execution.ui.ExecutionConsole
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.components.{Service, ServiceManager}
import com.intellij.openapi.project.{Project, ProjectManagerListener}

import javax.swing.{Icon, JComponent}
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.extensions.ObjectExt
import org.jetbrains.plugins.scala.icons.Icons
import org.jetbrains.plugins.scala.project.ProjectExt
import org.jetbrains.plugins.scala.util.ui.extensions.ComponentExt

import scala.concurrent.duration.DurationInt
import scala.jdk.CollectionConverters.CollectionHasAsScala

class CompilationChartsBuildToolWindowNodeFactory
  extends ProjectManagerListener {

  import CompilationChartsBuildToolWindowNodeFactory._

  override def projectOpened(project: Project): Unit = {
    def isJpsBuild(event: BuildEvent): Boolean = {
      val jpsActionClass = "com.intellij.compiler.impl.CompilerPropertiesAction"
      event.asOptionOf[StartBuildEvent]
        .flatMap(_.getBuildDescriptor.asOptionOf[DefaultBuildDescriptor])
        .exists { buildDescriptor =>
          val isJpsAction = buildDescriptor.getActions.asScala.exists(_.getClass.getName == jpsActionClass)
          val isUpToDateAction = buildDescriptor.getTitle.startsWith("Classes up-to-date check")
          isJpsAction && !isUpToDateAction
        }
    }

    val buildViewManager = project.getService(classOf[BuildViewManager])
    val listener: BuildProgressListener = { (buildId, event) =>
      if (isJpsBuild(event)) {
        val component = MainComponentHolder.createOrGet(project)
        buildViewManager.onEvent(buildId, new CompilationChartsBuildEvent(buildId, component))
      }
    }
    buildViewManager.addListener(listener, project.unloadAwareDisposable)
  }
}

object CompilationChartsBuildToolWindowNodeFactory {

  def refresh(project: Project): Unit =
    MainComponentHolder.createOrGet(project).repaint()

  private class CompilationChartsBuildEvent(buildId: AnyRef, component: JComponent)
    extends AbstractBuildEvent(
      new Object, buildId, System.currentTimeMillis(), ScalaBundle.message("compilation.charts.title")
    ) with PresentableBuildEvent {

    override lazy val getPresentationData: BuildEventPresentationData = new BuildEventPresentationData {
      override def getNodeIcon: Icon = Icons.COMPILATION_CHARTS

      override lazy val getExecutionConsole: ExecutionConsole = new ExecutionConsole {
        override def getComponent: JComponent = component
        override def getPreferredFocusableComponent: JComponent = component
        override def dispose(): Unit = ()
      }

      override def consoleToolbarActions(): ActionGroup = null
    }
  }

  @Service
  private final class MainComponentHolder {
    var mainComponent: Option[JComponent] = None
  }

  private object MainComponentHolder {

    private final val RefreshDelay = 1.second

    def createOrGet(project: Project): JComponent = {
      val holder = ServiceManager.getService(project, classOf[MainComponentHolder])
      holder.mainComponent.getOrElse {
        val component = new CompilationChartsComponent(project)
        holder.mainComponent = Some(component)
        component.bindExecutionToVisibility { () =>
          JobScheduler.getScheduler.scheduleWithFixedDelay(() => component.repaint(),
            0, RefreshDelay.length, RefreshDelay.unit
          )
        }
        component
      }
    }
  }
}
