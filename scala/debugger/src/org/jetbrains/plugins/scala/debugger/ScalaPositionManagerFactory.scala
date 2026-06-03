package org.jetbrains.plugins.scala.debugger

import com.intellij.debugger.engine.DebugProcess
import com.intellij.debugger.{PositionManager, PositionManagerFactory}
import org.jetbrains.plugins.scala.extensions.invokeLater
import org.jetbrains.plugins.scala.project.ProjectExt
import org.jetbrains.plugins.scala.statistics.ScalaDebuggerUsagesCollector

class ScalaPositionManagerFactory extends PositionManagerFactory {
  override def createPositionManager(process: DebugProcess): PositionManager = {
    invokeLater {
      if (process.getProject.hasScala) {
        ScalaDebuggerUsagesCollector.logDebugger(process.getProject)
      }
    }
    new ScalaPositionManager(process)
  }
}