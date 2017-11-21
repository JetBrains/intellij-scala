package org.jetbrains.plugins.scala.project.migration

import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.{PsiElement, PsiFile}
import org.jetbrains.plugins.scala.project.migration.api.{MigrationApiService, MigrationReport}

/**
  * User: Dmitry.Naydanov
  * Date: 25.07.16.
  */
protected trait ScalaLibraryMigrator {
  def migrateGlobal(projectStructure: MigrationApiService): Option[MigrationReport]

  def migrateLocal(file: PsiFile, localFixHolder: ProblemsHolder): Option[PartialFunction[PsiElement, Any]]

  def getName: String
  
  def getDescription: String
}
