package org.jetbrains.plugins.scala.debugger

import com.intellij.debugger.engine.DebugProcessImpl

private case class DebugProcessState(isAttached: Boolean, description: String)

private object DebugProcessState {
  def apply(debugProcess: DebugProcessImpl): DebugProcessState =
    if (debugProcess == null)
      DebugProcessState(isAttached = false, "Debug process not found")
    else if (debugProcess.isInInitialState)
      DebugProcessState(isAttached = false, "Debug process is in initial state")
    else if (debugProcess.isDetached)
      DebugProcessState(isAttached = false, "Debug process is detached")
    else if (debugProcess.isDetaching)
      DebugProcessState(isAttached = false, "Debug process is detaching")
    else
      DebugProcessState(isAttached = true, "Debug process is attached")
}

