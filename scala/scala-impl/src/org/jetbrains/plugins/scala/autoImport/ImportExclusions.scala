package org.jetbrains.plugins.scala.autoImport

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.ApiStatus

/**
 * The "Exclude from auto-import and completion" list of the project.
 * The list belongs to the Java code insight settings (IDE implementation), so the core only knows this interface.
 */
@ApiStatus.Internal
trait ImportExclusions {
  def isExcluded(qualifiedName: String, project: Project): Boolean
}

object ImportExclusions {
  def instance: ImportExclusions = ApplicationManager.getApplication.getService(classOf[ImportExclusions])
}
