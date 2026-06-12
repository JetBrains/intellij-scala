package org.jetbrains.sbt.project

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service

import kotlinx.coroutines.CoroutineScope

/** An application-level service created to provide an application-level coroutine scope. */
@Service(Array(Service.Level.APP))
private final class CoroutineAppScopeService(val coroutineScope: CoroutineScope)

object CoroutineAppScopeService {
  def coroutineScope: CoroutineScope =
    ApplicationManager.getApplication.getService(classOf[CoroutineAppScopeService]).coroutineScope
}
