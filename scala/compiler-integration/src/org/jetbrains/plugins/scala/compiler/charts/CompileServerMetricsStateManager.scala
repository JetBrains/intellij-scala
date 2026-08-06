package org.jetbrains.plugins.scala.compiler.charts

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.scala.settings.{ScalaCompileServerDefaults, ScalaCompileServerSettings}

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
    project.getService(classOf[MutableState])

  private def defaultValue: CompileServerMemoryState = {
    // This method is called to generate an "empty" CompileServerMemoryState value.
    // The exact value of the heapSize in this case doesn't matter. It will be quickly replaced by the value
    // returned from the server at runtime.
    val heapSizeSetting = ScalaCompileServerSettings.getInstance().COMPILE_SERVER_MAXIMUM_HEAP_SIZE
    val heapSizeMb = heapSizeSetting.toLongOption.getOrElse(ScalaCompileServerDefaults.DefaultHeapSize.toLong)
    val heapSize = heapSizeMb * 1024 * 1024
    CompileServerMemoryState(heapSize, Map.empty)
  }


  @Service
  private final class MutableState {
    var state: CompileServerMemoryState = defaultValue
  }
}
