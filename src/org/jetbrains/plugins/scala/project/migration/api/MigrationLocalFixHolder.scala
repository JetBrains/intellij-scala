package org.jetbrains.plugins.scala.project.migration.api

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement

/**
  * User: Dmitry.Naydanov
  * Date: 27.09.16.
  */
trait MigrationLocalFixHolder {
  def registerFix(psiElement: PsiElement, action: (Project) => Unit)
}
