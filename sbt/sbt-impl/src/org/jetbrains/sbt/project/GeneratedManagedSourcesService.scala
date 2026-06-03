package org.jetbrains.sbt.project

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project

import java.util.concurrent.ConcurrentHashMap

@Service(Array(Service.Level.PROJECT))
private final class GeneratedManagedSourcesService {
  private val generatedForPath: ConcurrentHashMap[String, java.lang.Boolean] = new ConcurrentHashMap()

  def generatedForPath(path: String): Boolean =
    generatedForPath.getOrDefault(path, java.lang.Boolean.FALSE)

  def setGeneratedForPath(path: String, value: Boolean): Unit = {
    generatedForPath.put(path, value)
  }
}

private object GeneratedManagedSourcesService {
  def instance(project: Project): GeneratedManagedSourcesService =
    project.getService(classOf[GeneratedManagedSourcesService])
}
