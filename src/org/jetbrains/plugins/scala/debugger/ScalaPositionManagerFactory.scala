package org.jetbrains.plugins.scala.debugger

import com.intellij.debugger.engine.DebugProcess
import com.intellij.debugger.{PositionManager, PositionManagerFactory}

/**
 * User: Alefas
 * Date: 14.10.11
 */
class ScalaPositionManagerFactory extends PositionManagerFactory {
  def createPositionManager(process: DebugProcess): PositionManager = {
    new ScalaPositionManager(process)
  }
}