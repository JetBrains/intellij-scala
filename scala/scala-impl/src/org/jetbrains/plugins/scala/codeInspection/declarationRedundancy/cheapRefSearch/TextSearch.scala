package org.jetbrains.plugins.scala.codeInspection.declarationRedundancy.cheapRefSearch

import com.intellij.psi.search.{PsiSearchHelper, TextOccurenceProcessor, UsageSearchContext}
import com.intellij.psi.{PsiElement, PsiReference}
import org.jetbrains.plugins.scala.codeInspection.declarationRedundancy.cheapRefSearch.Search.Pipeline.ShouldProcess
import org.jetbrains.plugins.scala.codeInspection.declarationRedundancy.cheapRefSearch.Search.{Method, SearchMethodResult}
import org.jetbrains.plugins.scala.util.ScalaUsageNamesUtil

import java.util.concurrent.ConcurrentLinkedQueue
import scala.jdk.CollectionConverters.CollectionHasAsScala

private[cheapRefSearch] final class TextSearch(override val shouldProcess: ShouldProcess) extends Method {

  override def searchForUsages(ctx: Search.Context): SearchMethodResult = {

    val buffer = new ConcurrentLinkedQueue[ElementUsage]()

    var didExitBeforeExhaustion = false

    val helper = PsiSearchHelper.getInstance(ctx.element.getProject)

    val processor = new TextOccurenceProcessor {

      override def execute(e2: PsiElement, offsetInElement: Int): Boolean = {

        if (ctx.element.getContainingFile == e2.getContainingFile) {
          true
        } else {

          val maybeUsage = e2 match {
            case r: PsiReference => Some(ElementUsageWithKnownReference(r, ctx.element))
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

    val stringsToSearch = ScalaUsageNamesUtil.getStringsToSearch(ctx.element).asScala.toSeq

    stringsToSearch.foreach { name =>
      helper.processElementsWithWord(processor, ctx.element.getUseScope, name,
        (UsageSearchContext.IN_CODE | UsageSearchContext.IN_FOREIGN_LANGUAGES).toShort, true)
    }

    new SearchMethodResult(buffer.asScala.toSeq, didExitBeforeExhaustion)
  }
}
