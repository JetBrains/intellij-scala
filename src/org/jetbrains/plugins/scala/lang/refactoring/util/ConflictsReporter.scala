package org.jetbrains.plugins.scala.lang.refactoring.util

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.util.containers.MultiMap

/**
 * @author Alexander Podkhalyuzin
 */

trait ConflictsReporter {
  def reportConflicts(project: Project, conflicts: MultiMap[PsiElement, String]): Boolean
}