package org.jetbrains.plugins.scala.findUsages.factory

import java.util
import java.util.Collections

import com.intellij.find.findUsages.FindUsagesOptions
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.{PsiElement, PsiNamedElement}
import com.intellij.usageView.UsageInfo
import com.intellij.util.Processor
import org.jetbrains.plugins.scala.findUsages.compilerReferences.search.CompilerIndicesReferencesSearch

/**
  * Find usages handler, which relies solely on compiler indices.
  *
  * See also: [[org.jetbrains.plugins.scala.findUsages.compilerReferences.ScalaCompilerReferenceService]]
  */
class CompilerIndicesFindUsagesHandler(
  e:       PsiElement,
  factory: ScalaFindUsagesHandlerFactory
) extends ScalaFindUsagesHandlerBase(e, factory) {

  override def processElementUsages(
    element:   PsiElement,
    processor: Processor[UsageInfo],
    options:   FindUsagesOptions
  ): Boolean = element match {
    case named: PsiNamedElement =>
      CompilerIndicesReferencesSearch
        .search(named)
        .forEach(ref => processor.process(new UsageInfo(ref)))
    case _ => false
  }

  override def processUsagesInText(
    element:     PsiElement,
    processor:   Processor[UsageInfo],
    searchScope: GlobalSearchScope
  ): Boolean = true

  override def getStringsToSearch(element: PsiElement): util.Collection[String] =
    Collections.emptyList()

  override def isSearchForTextOccurrencesAvailable(
    psiElement:   PsiElement,
    isSingleFile: Boolean
  ): Boolean = false
}
