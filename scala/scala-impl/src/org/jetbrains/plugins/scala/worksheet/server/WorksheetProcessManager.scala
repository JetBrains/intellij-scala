package org.jetbrains.plugins.scala
package worksheet.server

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.containers.ContainerUtil
import org.jetbrains.plugins.scala.compiler.CompilationProcess
object WorksheetProcessManager {

  private val processes = ContainerUtil.createConcurrentWeakMap[VirtualFile, CompilationProcess]()

  def add(file: VirtualFile, process: CompilationProcess): Unit = {
    process.addTerminationCallback(_ => remove(file))
    processes.put(file, process)
  }

  def remove(file: VirtualFile): Unit =
    processes.remove(file)

  def stop(file: VirtualFile): Unit = {
    val p = processes.get(file)
    if (p != null) p.stop()
  }

  def isRunning(file: VirtualFile): Boolean =
    processes containsKey file
}
