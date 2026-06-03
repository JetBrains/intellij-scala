package org.jetbrains.sbt.project

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project

import kotlinx.coroutines.CoroutineScope

/**
 * A service created for the only purpose of getting a coroutine scope from the platform.
 *
 * @note Any service can get a coroutine scope injected in its constructor.
 */
@Service(Array(Service.Level.PROJECT))
private final class CoroutineScopeService(val coroutineScope: CoroutineScope)

object CoroutineScopeService {
  // It's written in Scala 2 style because it's used in both Scala 2 & 3 modules
  implicit class ProjectExt(val project: Project) extends AnyVal {
    def coroutineScope: CoroutineScope =
      project.getService(classOf[CoroutineScopeService]).coroutineScope
  }
}