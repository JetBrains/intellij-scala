package org.jetbrains.plugins.scala.externalHighlighters

import java.util.function.Supplier

import com.intellij.compiler.CompilerManagerImpl
import com.intellij.openapi.compiler.CompilerManager
import com.intellij.openapi.project.impl.ProjectImpl
import com.intellij.openapi.project.{Project, ProjectManagerListener}
import com.intellij.task.ProjectTaskManager.Result
import com.intellij.task.ProjectTaskManager
import org.jetbrains.concurrency.Promise
import org.jetbrains.plugins.scala.components.ScalaPluginVersionVerifier

class OverridePlatformServices
  extends ProjectManagerListener {

  import OverridePlatformServices.{CustomProjectTaskManager, CustomCompilerManager}

  override def projectOpened(project: Project): Unit = {
    val projectImpl = project.asInstanceOf[ProjectImpl]
    val pluginDescriptor = ScalaPluginVersionVerifier.getPluginDescriptor

    projectImpl.registerService(
      classOf[ProjectTaskManager],
      classOf[CustomProjectTaskManager],
      pluginDescriptor,
      true
    )

    projectImpl.registerService(
      classOf[CompilerManager],
      classOf[CustomCompilerManager],
      pluginDescriptor,
      true
    )
  }
}


object OverridePlatformServices {

  @volatile private var compilationActive = false

  private class CustomProjectTaskManager(project: Project)
    extends DecoratePromiseProjectTaskManager(project) {

    override protected def decorate(supplier: Supplier[Promise[Result]]): Promise[Result] = {
      compilationActive = true
      supplier.get.onProcessed { _ =>
        compilationActive = false
      }
    }
  }

  private class CustomCompilerManager(project: Project)
    extends CompilerManagerImpl(project) {

    override def isCompilationActive: Boolean = compilationActive
  }
}
