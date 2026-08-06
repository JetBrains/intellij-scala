package org.jetbrains.plugins.scala.codeInspection.declarationRedundancy.cheapRefSearch

import com.intellij.lang.java.JavaLanguage
import com.intellij.openapi.project.Project
import com.intellij.psi.search.{PsiSearchHelper, TextOccurenceProcessor, UsageSearchContext}
import com.intellij.psi.{NavigatablePsiElement, PsiElement, PsiReference}
import org.jetbrains.plugins.scala.ScalaLanguage
import org.jetbrains.plugins.scala.codeInspection.declarationRedundancy.cheapRefSearch.Search.Pipeline.ShouldProcess
import org.jetbrains.plugins.scala.codeInspection.declarationRedundancy.cheapRefSearch.Search.{Method, SearchMethodResult}
import org.jetbrains.plugins.scala.codeInspection.declarationRedundancy.cheapRefSearch.TextSearch.{isNotScalaOrJava, isUReferenceExpression}
import org.jetbrains.plugins.scala.util.ScalaUsageNamesUtil
import org.jetbrains.uast.{UReferenceExpression, UastFacade}

import java.util.concurrent.ConcurrentLinkedQueue
import scala.jdk.CollectionConverters.CollectionHasAsScala

private[cheapRefSearch] final class TextSearch(
  override val shouldProcess: ShouldProcess,
  project: Project
) extends Method {

  private val psiSearchHelper: PsiSearchHelper = PsiSearchHelper.getInstance(project)

  override def searchForUsages(ctx: Search.Context): SearchMethodResult = {
    val buffer = new ConcurrentLinkedQueue[ElementUsage]()
    var didExitBeforeExhaustion = false
    val searchedElement = ctx.element

    val processor = new TextOccurenceProcessor {
      override def execute(foundElement: PsiElement, offsetInElement: Int): Boolean = {
        if (foundElement.getContainingFile == searchedElement.getContainingFile) {
          true
        } else {
          val found = foundElement match {
            case _: PsiReference => true
            case n: NavigatablePsiElement if isNotScalaOrJava(n) && isUReferenceExpression(n) => true
            case _ => false
          }

          if (found) {
            val usage = ElementUsageWithKnownReference(foundElement, searchedElement)
            buffer.add(usage)
            if (ctx.canExit(usage)) {
              didExitBeforeExhaustion = true
              false // exit
            } else {
              true // continue
            }
          } else {
            true // continue
          }
        }
      }
    }

    val useScope = psiSearchHelper.getUseScope(searchedElement)
    val stringsToSearch = ScalaUsageNamesUtil.getStringsToSearch(searchedElement).asScala.toSeq
    stringsToSearch
      .iterator
      .takeWhile(_ => !didExitBeforeExhaustion)
      .foreach { name =>
        psiSearchHelper.processElementsWithWord(processor, useScope, name,
          (UsageSearchContext.IN_CODE | UsageSearchContext.IN_FOREIGN_LANGUAGES).toShort, true)
      }

    new SearchMethodResult(buffer.asScala.toSeq, didExitBeforeExhaustion)
  }
}

object TextSearch {
  private val uastFacade: UastFacade = UastFacade.INSTANCE

  private def isNotScalaOrJava(e: PsiElement): Boolean =
    !e.getLanguage.isKindOf(ScalaLanguage.INSTANCE) && !e.getLanguage.isKindOf(JavaLanguage.INSTANCE)

  private def isUReferenceExpression(e: PsiElement): Boolean =
    uastFacade.convertElementWithParent(e, classOf[UReferenceExpression]) != null
}