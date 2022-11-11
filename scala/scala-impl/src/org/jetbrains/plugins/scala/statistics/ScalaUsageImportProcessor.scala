package org.jetbrains.plugins.scala.statistics

import com.intellij.internal.statistic.libraryUsage.LibraryUsageImportProcessor
import com.intellij.psi.{PsiElement, PsiFile}
import org.jetbrains.plugins.scala.extensions.{IteratorExt, PsiElementExt}
import org.jetbrains.plugins.scala.lang.psi.ScImportsHolder
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports.ScImportExpr

import java.util
import scala.jdk.CollectionConverters.SeqHasAsJava

class ScalaUsageImportProcessor extends LibraryUsageImportProcessor[ScImportExpr] {
  override def imports(psiFile: PsiFile): util.List[ScImportExpr] = psiFile match {
    case scalaFile: ScalaFile =>
      scalaFile
        .depthFirst()
        .filterByType[ScImportsHolder]
        .flatMap(_.getImportStatements)
        .flatMap(_.importExprs)
        .toList.asJava
    case _ =>
      java.util.List.of()
  }

  override def importQualifier(importExpr: ScImportExpr): String =
    importExpr.qualifier.map(_.qualName).orNull

  override def isSingleElementImport(importExpr: ScImportExpr): Boolean =
    importExpr.selectorSet.isEmpty && !importExpr.hasWildcardSelector

  override def resolve(importExpr: ScImportExpr): PsiElement =
    importExpr.qualifier.map(_.resolve()).orNull
}
