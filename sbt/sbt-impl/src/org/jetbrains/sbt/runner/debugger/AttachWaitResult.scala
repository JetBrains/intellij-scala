package org.jetbrains.sbt.runner.debugger

private enum AttachWaitResult {
  case AlreadyAttached
  case AttachedAfterWaiting
}