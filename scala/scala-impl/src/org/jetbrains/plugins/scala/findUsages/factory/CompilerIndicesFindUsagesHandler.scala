package org.jetbrains.plugins.scala.findUsages.factory

import com.intellij.find.findUsages.FindUsagesOptions
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.searches.ClassInheritorsSearch
import com.intellij.psi.{PsiClass, PsiElement, PsiMethod, PsiNamedElement}
import com.intellij.usageView.UsageInfo
import com.intellij.util.Processor
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.findUsages.ExternalReferenceSearcher
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTypeDefinition

import java.util
import java.util.Collections

/**
  * Find usages handler, which relies solely on compiler indices.
  *
  * See also: `org.jetbrains.plugins.scala.compiler.references.ScalaCompilerReferenceService`
  */
class CompilerIndicesFindUsagesHandler(
  e:       PsiElement,
  factory: ScalaFindUsagesHandlerFactory
) extends ScalaFindUsagesHandlerBase(e, factory) {
  private[this] val pfindex = ProjectFileIndex.getInstance(e.getProject)

  private[this] def isInLibrary(element: PsiElement): Boolean = inReadAction {
    (for {
      file  <- element.getContainingFile.toOption
      vfile <- file.getVirtualFile.toOption
    } yield pfindex.isInLibrary(vfile)).getOrElse(false)
  }

  private[this] def searchInCompilerIndices(
    e:         PsiNamedElement,
    processor: Processor[_ >: UsageInfo]
  ): Boolean = {
    //noinspection ApiStatus
    ExternalReferenceSearcher
      .searchExternally(e)
      .forEach(ref => processor.process(new UsageInfo(ref)))
  }

  override def processElementUsages(
    element:   PsiElement,
    processor: Processor[_ >: UsageInfo],
    options:   FindUsagesOptions
  ): Boolean = element match {
    case (named: PsiNamedElement) & ContainingClass(cls: ScTypeDefinition) if isInLibrary(element) =>
      val res = searchInCompilerIndices(named, processor)
      // this inheritor-traversing logic is already present in ScalaCompilerReferenceReader,
      // but as library jars are not indexed, we have to partially rely on standard searchers instead.
      if (res) {
        ClassInheritorsSearch.search(cls).forEach(inheritor => {
          val inherited = named match {
            case cls: PsiClass     => inheritor.findInnerClassByName(cls.name, true).toOption
            case method: PsiMethod => inheritor.findMethodBySignature(method, true).toOption
            case _                 => inheritor.findMethodsByName(named.name, true).find(m => !m.hasParameters)
          }
          inherited.forall(searchInCompilerIndices(_, processor))
        })
      } else false
    case named: PsiNamedElement => searchInCompilerIndices(named, processor)
    case _                      => false
  }

  override def processUsagesInText(
    element:     PsiElement,
    processor:   Processor[_ >: UsageInfo],
    searchScope: GlobalSearchScope
  ): Boolean = true

  override def getStringsToSearch(element: PsiElement): util.Collection[String] =
    Collections.emptyList()

  override def isSearchForTextOccurrencesAvailable(
    psiElement:   PsiElement,
    isSingleFile: Boolean
  ): Boolean = false
}
