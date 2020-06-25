package org.jetbrains.sbt.project.structure

trait Cancellable {
  def cancel(): Unit
}
