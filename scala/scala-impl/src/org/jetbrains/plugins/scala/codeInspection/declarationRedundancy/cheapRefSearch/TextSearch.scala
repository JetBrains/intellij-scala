package org.jetbrains.plugins.scala.codeInspection.declarationRedundancy.cheapRefSearch

import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.intellij.psi.search.{PsiSearchHelper, TextOccurenceProcessor, UsageSearchContext}
import com.intellij.psi.{PsiElement, PsiIdentifier}
import org.jetbrains.plugins.scala.codeInspection.declarationRedundancy.cheapRefSearch.Search.Pipeline.ShouldProcess
import org.jetbrains.plugins.scala.codeInspection.declarationRedundancy.cheapRefSearch.Search.{Method, SearchMethodResult}
import org.jetbrains.plugins.scala.extensions.{Parent, PsiElementExt}
import org.jetbrains.plugins.scala.lang.psi.api.base.ScStableCodeReference
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScReferencePattern
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunctionDefinition
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTypeDefinition
import org.jetbrains.plugins.scala.util.ScalaUsageNamesUtil

import java.util.concurrent.ConcurrentLinkedQueue
import scala.jdk.CollectionConverters.CollectionHasAsScala

private[cheapRefSearch] final class TextSearch(override val shouldProcess: ShouldProcess) extends Method {

  private val buffer: ConcurrentLinkedQueue[ElementUsage] = new ConcurrentLinkedQueue()

  override def searchForUsages(ctx: Search.Context): SearchMethodResult = {

    var didExitBeforeExhaustion = false

    val helper = PsiSearchHelper.getInstance(ctx.element.getProject)

    val processor = new TextOccurenceProcessor {

      override def execute(e2: PsiElement, offsetInElement: Int): Boolean = {

        if (ctx.element.getContainingFile == e2.getContainingFile) {
          true
        } else {
          val maybeUsage = e2 match {
            case Parent(_: ScReferencePattern) => None
            case Parent(_: ScTypeDefinition) => None
            case Parent(_: ScFunctionDefinition) => None
            case _: PsiIdentifier =>
              Some(ElementUsageWithKnownReference(e2, ctx.element))
            case l: LeafPsiElement if l.isIdentifier =>
              Some(ElementUsageWithKnownReference(e2, ctx.element))
            case _: ScStableCodeReference =>
              Some(ElementUsageWithKnownReference(e2, ctx.element))
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

    val result = new SearchMethodResult(buffer.asScala.toSeq, didExitBeforeExhaustion)

    buffer.clear()

    result
  }
}
