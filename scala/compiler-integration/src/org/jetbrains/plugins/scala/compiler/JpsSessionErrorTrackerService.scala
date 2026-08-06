package org.jetbrains.plugins.scala.compiler

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import scala.annotation.unused

@Service(Array(Service.Level.PROJECT))
private final class JpsSessionErrorTrackerService(@unused project: Project) extends Disposable {

  private val jpsSessionSet: ConcurrentHashMap[UUID, java.lang.Boolean] = new ConcurrentHashMap()

  def register(uuid: UUID): Unit = {
    jpsSessionSet.put(uuid, java.lang.Boolean.TRUE)
  }

  def hasError(uuid: UUID): Boolean = {
    val result = jpsSessionSet.remove(uuid)
    result ne null
  }

  override def dispose(): Unit = {
    jpsSessionSet.clear()
  }
}

private object JpsSessionErrorTrackerService {
  def instance(project: Project): JpsSessionErrorTrackerService =
    project.getService(classOf[JpsSessionErrorTrackerService])
}
