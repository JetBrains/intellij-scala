package org.jetbrains.plugins.scala.codeInspection.declarationRedundancy.cheapRefSearch

import org.jetbrains.plugins.scala.annotator.usageTracker.ScalaRefCountHolder
import org.jetbrains.plugins.scala.codeInspection.declarationRedundancy.cheapRefSearch.Search.{SearchMethodResult, Method}
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

    var used = false

    val success = refCountHolder.runIfUnusedReferencesInfoIsAlreadyRetrievedOrSkip { () =>
      used = refCountHolder.isValueReadUsed(ctx.element) || refCountHolder.isValueWriteUsed(ctx.element)
    }

    val res = if (!success || used) {
      // ElementUsageWithoutReference is used because ScalaRefCountHolder does not remember
      // reference sources, only their targets.
      Seq(ElementUsageWithoutReference)
    } else {
      Seq.empty
    }

    new SearchMethodResult(res, !success)
  }
}
