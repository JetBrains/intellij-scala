package org.jetbrains.plugins.scala.externalSystem.util

import com.intellij.openapi.components.ComponentManager

/**
 * Reimplementation of deprecated API `com.intellij.openapi.externalSystem.util.DisposeAwareProjectChange`.
 */
abstract class DisposeAwareProjectChange(componentManager: ComponentManager) extends Runnable {
  def execute(): Unit

  override def run(): Unit = {
    if (!componentManager.isDisposed) {
      execute()
    }
  }
}
