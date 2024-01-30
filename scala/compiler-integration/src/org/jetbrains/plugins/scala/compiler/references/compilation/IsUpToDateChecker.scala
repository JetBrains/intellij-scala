package org.jetbrains.plugins.scala.compiler.references.compilation

import com.intellij.compiler.backwardRefs.IsUpToDateCheckConsumer
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.scala.compiler.executeOnBuildThread
import org.jetbrains.plugins.scala.compiler.references.ScalaCompilerReferenceService

private final class IsUpToDateChecker extends IsUpToDateCheckConsumer {
  override def isApplicable(project: Project): Boolean = {
    val service = ScalaCompilerReferenceService(project)
    service.initialized && service.hasIndex
  }

  override def isUpToDate(project: Project, isUpToDate: Boolean): Unit = {
    if (!isUpToDate) return
    val service = ScalaCompilerReferenceService(project)
    executeOnBuildThread(() => service.markUpToDate())
  }
}
