package org.jetbrains.plugins.scala
package worksheet.server

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.containers.ConcurrentWeakHashMap
import org.jetbrains.plugins.scala.components.WorksheetProcess

/**
 * User: Dmitry Naydanov
 * Date: 2/20/14
 */
object WorksheetProcessManager {
  private val processes = new ConcurrentWeakHashMap[VirtualFile, WorksheetProcess]()

  def add(file: VirtualFile, process: WorksheetProcess) {
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
