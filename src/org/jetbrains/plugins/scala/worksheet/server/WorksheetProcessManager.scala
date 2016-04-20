package org.jetbrains.plugins.scala
package worksheet.server

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.containers.ContainerUtil
import org.jetbrains.plugins.scala.compiler.CompilationProcess

/**
 * User: Dmitry Naydanov
 * Date: 2/20/14
 */
object WorksheetProcessManager {
  private val processes = ContainerUtil.createConcurrentWeakMap[VirtualFile, CompilationProcess]()

  def add(file: VirtualFile, process: CompilationProcess) {
    process.addTerminationCallback({remove(file)})
    processes.put(file, process)
  }

  def remove(file: VirtualFile) {
    processes.remove(file)
  }
  
  def stop(file: VirtualFile) {
    val p = processes.get(file)
    if (p != null) p.stop()
  }

  def running(file: VirtualFile) = processes containsKey file
}
