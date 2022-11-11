package org.jetbrains.plugins.scala.codeInspection.declarationRedundancy.cheapRefSearch

import org.jetbrains.plugins.scala.codeInspection.declarationRedundancy.cheapRefSearch.Search.{SearchMethodResult, Method}
import org.jetbrains.plugins.scala.codeInspection.declarationRedundancy.cheapRefSearch.Search.Pipeline.ShouldProcess
import org.jetbrains.plugins.scala.extensions.PsiElementExt
import org.jetbrains.plugins.scala.util.ImplicitUtil.ImplicitTargetExt

private[cheapRefSearch] final class LocalImplicitSearch(override val shouldProcess: ShouldProcess) extends Method {

  override def searchForUsages(ctx: Search.Context): SearchMethodResult = {

    /**
     * ElementUsageWithoutReference is used because in our current implementation we never
     * use the reference that refOrImplicitRefIn yields. We might start using it in the
     * future. This would probably require amending ElementUsageWithReference#targetCanBePrivate.
     * For now we assume that if a local implicit is used, its access level can't be tightened.
     *
     * Note that we stop inspecting elements as soon as the first one is found!
     *
     * More than one ElementUsageWithoutReference result does not yield different inspections results.
     * The moment we're going to return something other than ElementUsageWithoutReference
     * here, please reconsider the right-hand side. Probably you need to get rid of `.find` and
     * replace it with `.map`.
     */

    val res = ctx.element.getContainingFile.depthFirst()
      .find(ctx.element.refOrImplicitRefIn(_).nonEmpty)
      .map(_ => ElementUsageWithUnknownReference).toSeq

    new SearchMethodResult(res, didExitBeforeExhaustion = false)
  }

}
