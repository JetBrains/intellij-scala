package org.jetbrains.sbt.shell.communication

import org.jetbrains.annotations.ApiStatus.Experimental

/**
 * Event emitted while a command submitted to the sbt shell is being processed.
 */
@Experimental
enum ShellEvent {
  case TaskStart
  case TaskComplete
  case ProcessTerminated
  case ErrorWaitForInput
  case Output(line: String)
}
