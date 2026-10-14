package org.jetbrains.plugins.scala.lang.psi

import com.intellij.openapi.application.ApplicationManager
import com.intellij.psi.PsiElement
import org.jetbrains.annotations.{ApiStatus, Nullable}
import org.jetbrains.plugins.scala.lang.psi.ScImportsHolder.ImportPath
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports.ScImportExpr

/**
 * Inserts import statements into an [[ScImportsHolder]].
 * The insertion lays out imports the way "Optimize Imports" does, which is editor code, so the PSI knows only this interface.
 */
@ApiStatus.Internal
trait ImportInserter {

  /** @see [[ScImportsHolder.addImportsForPaths]] */
  def addImportsForPaths(holder: ScImportsHolder, paths: Seq[ImportPath], @Nullable refsContainer: PsiElement): Unit

  /** @see [[ScImportsHolder.bindImportSelectorOrExpression]] */
  def bindImportSelectorOrExpression(
    holder: ScImportsHolder,
    importExpr: ScImportExpr,
    oldImportExprOrSelectorToDelete: PsiElement,
    newImportPath: ImportPath
  ): Unit
}

object ImportInserter {
  def instance: ImportInserter = ApplicationManager.getApplication.getService(classOf[ImportInserter])
}
