package org.jetbrains.plugins.scala.debugger.breakpoints

import com.intellij.debugger.engine.{DebugProcessImpl, JavaBreakpointHandler, JavaBreakpointHandlerFactory}

/**
 * @author Nikolay.Tropin
 */
class ScalaBreakpointHandlerFactory extends JavaBreakpointHandlerFactory {
  override def createHandler(process: DebugProcessImpl): JavaBreakpointHandler = new ScalaBreakpointHandler(process)
}

class ScalaBreakpointHandler(process: DebugProcessImpl) extends JavaBreakpointHandler(classOf[ScalaLineBreakpointType], process)