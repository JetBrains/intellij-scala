package org.jetbrains.plugins.scala.compilationCharts

import com.intellij.openapi.components.{Service, ServiceManager}
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.scala.compiler.ScalaCompileServerSettings

object CompileServerMetricsStateManager {

  def get(project: Project): CompileServerMemoryState =
    mutableState(project).state

  def update(project: Project, newState: CompileServerMemoryState): CompileServerMemoryState = {
    mutableState(project).state = newState
    newState
  }

  def reset(project: Project): CompileServerMemoryState =
    update(project, defaultValue)

  private def mutableState(project: Project): MutableState =
    ServiceManager.getService(project, classOf[MutableState])

  private def defaultValue: CompileServerMemoryState = {
    val heapSizeMb = ScalaCompileServerSettings.getInstance.COMPILE_SERVER_MAXIMUM_HEAP_SIZE.toLong
    val heapSize = heapSizeMb * 1024 * 1024
    CompileServerMemoryState(heapSize, Map.empty)
  }


  @Service
  private final class MutableState {
    var state: CompileServerMemoryState = defaultValue
  }
}
