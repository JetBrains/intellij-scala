package org.jetbrains.plugins.scala.compiler.references.search

import com.intellij.psi.{PsiClass, PsiElement}
import com.intellij.util.Processor
import org.jetbrains.plugins.scala.compiler.references.search.UsageToPsiElements._
import org.jetbrains.plugins.scala.compiler.references.{ScalaCompilerReferenceService, UsagesInFile}
import org.jetbrains.plugins.scala.util.SAMUtil.SAMTypeImplementation
import org.jetbrains.plugins.scala.util.ScEquivalenceUtil

/**
  * Allows to search for inheritors of potentially SAMable traits & clases
  */
class CompilerIndicesInheritorsSearcher
    extends CompilerIndicesSearcher[
      PsiClass,
      PsiElement,
      CompilerIndicesInheritorsSearch.SearchParameters
    ](true) {

  override def processQuery(
    params:    CompilerIndicesInheritorsSearch.SearchParameters,
    processor: Processor[_ >: PsiElement]
  ): Unit = {
    val cls     = params.cls
    val project = cls.getProject
    val service = ScalaCompilerReferenceService(project)
    val usages  = service.SAMImplementationsOf(cls, checkDeep = false)
    processResultsFromCompilerService(cls, usages, project, processor)
  }

  override protected def processMatchingElements(
    target:             PsiClass,
    usage:              UsagesInFile,
    isPossiblyOutdated: Boolean,
    candidates:         ElementsInContext,
    processor:          Processor[_ >: PsiElement]
  ): Boolean = {
    val matched: Seq[(PsiElement, Int)] = candidates.elements.collect {
      case (e @ SAMTypeImplementation(sam), line) if ScEquivalenceUtil.areClassesEquivalent(sam, target) =>
        processor.process(e)
        (e, line)
      case (cls: PsiClass, line) if cls.isInheritor(target, true) =>
        processor.process(cls)
        (cls, line)
    }

    val extraLines = usage.lines.diff(matched.map(_._2))
    extraLines.isEmpty
  }
}
