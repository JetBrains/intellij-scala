package org.jetbrains.plugins.scala.codeInspection.declarationRedundancy.cheapRefSearch

import com.intellij.psi.PsiElement
import com.intellij.psi.search.{PsiSearchHelper, TextOccurenceProcessor, UsageSearchContext}
import org.jetbrains.plugins.scala.codeInspection.declarationRedundancy.cheapRefSearch.Search.{SearchMethodResult, Method}
import org.jetbrains.plugins.scala.codeInspection.declarationRedundancy.cheapRefSearch.Search.Pipeline.ShouldProcess
import org.jetbrains.plugins.scala.extensions.PsiFileExt
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScEnumCase
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScEnum

import scala.collection.mutable.ListBuffer

private[cheapRefSearch] final class ForeignEnumSearch(override val shouldProcess: ShouldProcess) extends Method {

  private val CaseSensitive = true
  private val PsiSearchContext = (UsageSearchContext.IN_CODE | UsageSearchContext.IN_FOREIGN_LANGUAGES).toShort

  override def searchForUsages(ctx: Search.Context): SearchMethodResult = {

    val result = new ListBuffer[ElementUsage]()

    val scope = ctx.element.getUseScope

    var didExitBeforeExhaustion = false

    val processor = new TextOccurenceProcessor {
      override def execute(e2: PsiElement, offsetInElement: Int): Boolean =
        if (e2.getContainingFile.isScala2File || e2.getContainingFile.isScala3File) {
          true
        } else {

          val usage = ElementUsageWithKnownReference(e2, ctx.element)
          result.addOne(usage)

          val continue = !ctx.canExit(usage)

          if (!continue) {
            didExitBeforeExhaustion = true
          }

          continue
        }
    }

    val scEnum: Option[ScEnum] = ctx.element match {
      case el: ScEnumCase => Some(el.enumParent)
      case el: ScEnum => Some(el)
      case _ => None
    }

    scEnum.foreach { enum =>
      PsiSearchHelper.getInstance(ctx.element.getProject)
        .processElementsWithWord(processor, scope, enum.name, PsiSearchContext, CaseSensitive)

      if (!didExitBeforeExhaustion) {
        PsiSearchHelper.getInstance(ctx.element.getProject)
          .processElementsWithWord(processor, scope, s"${enum.name}$$.MODULE$$", PsiSearchContext, CaseSensitive)
      }
    }

    new SearchMethodResult(result.toSeq, didExitBeforeExhaustion)
  }

}
