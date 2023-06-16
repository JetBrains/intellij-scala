package org.jetbrains.plugins.scala.statistics

import com.intellij.internal.statistic.libraryUsage.LibraryUsageImportProcessor
import com.intellij.psi.{PsiElement, PsiFile}
import org.jetbrains.plugins.scala.extensions.{IteratorExt, PsiElementExt}
import org.jetbrains.plugins.scala.lang.psi.ScImportsHolder
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScPackaging
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports.{ScImportExpr, ScImportStmt}

import java.util
import scala.jdk.CollectionConverters.SeqHasAsJava

//noinspection UnstableApiUsage
class ScalaUsageImportProcessor extends LibraryUsageImportProcessor[ScImportExpr] {
  override def imports(psiFile: PsiFile): util.List[ScImportExpr] = psiFile match {
    case scalaFile: ScalaFile =>
      val importStatements = collectImportsStatements(scalaFile)
      val importExpressions = importStatements.flatMap(_.importExprs)
      importExpressions.asJava
    case _ =>
      util.List.of()
  }

  override def importQualifier(importExpr: ScImportExpr): String =
    importExpr.qualifier.map(_.qualName).orNull

  override def isSingleElementImport(importExpr: ScImportExpr): Boolean =
    importExpr.selectorSet.isEmpty && !importExpr.hasWildcardSelector

  override def resolve(importExpr: ScImportExpr): PsiElement =
    importExpr.qualifier.map(_.resolve()).orNull

  private def collectImportsStatements(fileOrPackage: ScImportsHolder): Seq[ScImportStmt] = {
    val nestedPackagings = fileOrPackage.children.filterByType[ScPackaging]
    fileOrPackage.getImportStatements ++ nestedPackagings.flatMap(collectImportsStatements)
  }
}
