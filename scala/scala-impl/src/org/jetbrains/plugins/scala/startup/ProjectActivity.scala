package org.jetbrains.plugins.scala.startup

import com.intellij.openapi.project.Project
import com.intellij.util.JavaCoroutines
import kotlin.coroutines.Continuation

/**
 * Scala shim around [[com.intellij.openapi.startup.ProjectActivity]] to avoid exposing coroutine internals
 * when implementing project startup activities.
 */
trait ProjectActivity extends com.intellij.openapi.startup.ProjectActivity {
  def execute(project: Project): Unit

  final override def execute(project: Project, continuation: Continuation[_ >: kotlin.Unit]): AnyRef = {
    //noinspection ApiStatus,UnstableApiUsage
    JavaCoroutines.suspendJava[kotlin.Unit](cont => {
      execute(project)
      cont.resume(kotlin.Unit.INSTANCE)
    }, continuation)
  }
}
