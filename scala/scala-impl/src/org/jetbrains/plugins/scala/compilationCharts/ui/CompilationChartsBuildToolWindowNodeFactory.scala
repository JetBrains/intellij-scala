package org.jetbrains.plugins.scala.compilationCharts.ui

import com.intellij.build.events.impl.AbstractBuildEvent
import com.intellij.build.events.{BuildEvent, BuildEventPresentationData, PresentableBuildEvent, StartBuildEvent}
import com.intellij.build.{BuildProgressListener, BuildViewManager, DefaultBuildDescriptor}
import com.intellij.execution.ui.ExecutionConsole
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.project.{Project, ProjectManagerListener}
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.compiler.{CompileServerLauncher, ScalaCompileServerSettings}
import org.jetbrains.plugins.scala.extensions.ObjectExt
import org.jetbrains.plugins.scala.icons.Icons
import org.jetbrains.plugins.scala.project.ProjectExt

import javax.swing.{Icon, JComponent}
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

    def compileServerEnabled: Boolean =
      ScalaCompileServerSettings.getInstance.COMPILE_SERVER_ENABLED || CompileServerLauncher.running

    val buildViewManager = project.getService(classOf[BuildViewManager])
    val listener: BuildProgressListener = { (buildId, event) =>
      if (compileServerEnabled && project.hasScala && isJpsBuild(event)) {
        val component = CompilationChartsComponentHolder.createOrGet(project)
        buildViewManager.onEvent(buildId, new CompilationChartsBuildEvent(buildId, component))
      }
    }
    buildViewManager.addListener(listener, project.unloadAwareDisposable)
  }
}

object CompilationChartsBuildToolWindowNodeFactory {

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
}
