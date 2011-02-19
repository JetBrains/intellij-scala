package org.jetbrains.plugins.scala.lang.refactoring.util

import com.intellij.openapi.project.Project

/**
 * @author Alexander Podkhalyuzin
 */

trait ConflictsReporter {
  def reportConflicts(conflicts: Array[String], project: Project): Boolean
}