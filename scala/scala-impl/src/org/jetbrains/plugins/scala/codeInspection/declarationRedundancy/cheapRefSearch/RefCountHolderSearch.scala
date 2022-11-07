package org.jetbrains.plugins.scala.codeInspection.declarationRedundancy.cheapRefSearch

import com.intellij.model.Pointer
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.annotator.usageTracker.ScalaRefCountHolder
import org.jetbrains.plugins.scala.codeInspection.declarationRedundancy.cheapRefSearch.Search.{Method, SearchMethodResult}
import org.jetbrains.plugins.scala.codeInspection.declarationRedundancy.cheapRefSearch.Search.Pipeline.ShouldProcess

/**
 * Remember there are currently 2 problems in ScalaRefCountHolder:
 * - It does not remember reference sources, only their targets. This makes it impossible to partially
 * invalidate its `myValueUsed` cache.
 * - It's a moody entity, which it explicitly models via `ScalaRefCountHolder#isReady()`. And when it
 * is not ready, there is no fallback mechanism.
 *
 * These problems cause flaky, unreliable behaviour for everything that uses ScalaRefCountHolder. It also forces
 * consumers of ScalaRefCountHolder to deal with its potential unreadiness. Also see SCL-19970.
 */

private[cheapRefSearch] final class RefCountHolderSearch(override val shouldProcess: ShouldProcess) extends Method {

  override def searchForUsages(ctx: Search.Context): SearchMethodResult = {
    val refCountHolder: ScalaRefCountHolder = ScalaRefCountHolder(ctx.element)

    var references: Seq[Pointer[PsiElement]] = Seq.empty

    val success = refCountHolder.runIfUnusedReferencesInfoIsAlreadyRetrievedOrSkip { () =>
      references = refCountHolder.getReadReferences(ctx.element) ++ refCountHolder.getWriteReferences(ctx.element)
    }

    val res = if (!success) {
      // ElementUsageWithoutReference is used because ScalaRefCountHolder
      // was not ready yet, so we assume the element is used.
      Seq(ElementUsageWithoutReference)
    } else {
      references.map(r => ElementUsageWithReference(r, ctx.element))
    }

    new SearchMethodResult(res, !success)
  }
}
