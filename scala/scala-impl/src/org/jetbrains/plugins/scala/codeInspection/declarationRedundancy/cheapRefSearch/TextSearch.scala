package org.jetbrains.plugins.scala.codeInspection.declarationRedundancy.cheapRefSearch

import com.intellij.lang.java.JavaLanguage
import com.intellij.openapi.project.Project
import com.intellij.psi.search.{PsiSearchHelper, TextOccurenceProcessor, UsageSearchContext}
import com.intellij.psi.{NavigatablePsiElement, PsiElement, PsiReference}
import org.jetbrains.plugins.scala.ScalaLanguage
import org.jetbrains.plugins.scala.codeInspection.declarationRedundancy.cheapRefSearch.Search.Pipeline.ShouldProcess
import org.jetbrains.plugins.scala.codeInspection.declarationRedundancy.cheapRefSearch.Search.{Method, SearchMethodResult}
import org.jetbrains.plugins.scala.lang.psi.light.{PsiClassWrapper, PsiMethodWrapper}
import org.jetbrains.plugins.scala.util.ScalaUsageNamesUtil
import org.jetbrains.uast.{UReferenceExpression, UastFacade}

import java.util.concurrent.ConcurrentLinkedQueue
import scala.jdk.CollectionConverters.CollectionHasAsScala

private[cheapRefSearch] final class TextSearch(
  override val shouldProcess: ShouldProcess,
  project: Project
) extends Method {

  private val psiSearchHelper: PsiSearchHelper = PsiSearchHelper.getInstance(project)
  private val uastFacade: UastFacade = UastFacade.INSTANCE

  override def searchForUsages(ctx: Search.Context): SearchMethodResult = {

    val buffer = new ConcurrentLinkedQueue[ElementUsage]()

    var didExitBeforeExhaustion = false

    val psiElement = ctx.element

    val processor = new TextOccurenceProcessor {

      override def execute(e2: PsiElement, offsetInElement: Int): Boolean = {

        if (psiElement.getContainingFile == e2.getContainingFile) {
          true
        } else {

          val maybeUsage = e2 match {
            case r: PsiReference =>
              Some(ElementUsageWithKnownReference(r, psiElement))
            case n: NavigatablePsiElement if !n.getLanguage.isInstanceOf[ScalaLanguage] && !n.getLanguage.isInstanceOf[JavaLanguage] =>

              val uElement = uastFacade.convertElement(n, null, null)

              uElement match {
                case r: UReferenceExpression =>
                  r.resolve() match {
                    case wrapper: PsiClassWrapper if wrapper.definition == psiElement =>
                      Some(ElementUsageWithKnownReference(n, psiElement))
                    case wrapper: PsiMethodWrapper[_] if wrapper.delegate == psiElement =>
                      Some(ElementUsageWithKnownReference(n, psiElement))
                    case _ => None
                  }
                case _ => None
              }
            case _ => None
          }

          val continue = maybeUsage.forall { usage =>
            buffer.add(usage)
            !ctx.canExit(usage)
          }

          if (!continue) didExitBeforeExhaustion = true
          continue
        }
      }
    }

    val useScope = psiSearchHelper.getUseScope(psiElement)
    val stringsToSearch = ScalaUsageNamesUtil.getStringsToSearch(psiElement).asScala.toSeq
    stringsToSearch.foreach { name =>
      psiSearchHelper.processElementsWithWord(processor, useScope, name,
        (UsageSearchContext.IN_CODE | UsageSearchContext.IN_FOREIGN_LANGUAGES).toShort, true)
    }

    new SearchMethodResult(buffer.asScala.toSeq, didExitBeforeExhaustion)
  }
}
