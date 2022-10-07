package org.jetbrains.plugins.scala.codeInspection.declarationRedundancy.cheapRefSearch

import com.intellij.psi.PsiReference
import com.intellij.psi.search.LocalSearchScope
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.util.Processor
import org.jetbrains.plugins.scala.codeInspection.declarationRedundancy.cheapRefSearch.Search.{SearchMethodResult, Method}
import org.jetbrains.plugins.scala.codeInspection.declarationRedundancy.cheapRefSearch.Search.Pipeline.ShouldProcess
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScEnumCase
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScNamedElement
import org.jetbrains.plugins.scala.util.ScalaUsageNamesUtil

import scala.collection.mutable.ListBuffer

private[cheapRefSearch] final class LocalRefSearch(override val shouldProcess: ShouldProcess) extends Method {
  override def searchForUsages(ctx: Search.Context): SearchMethodResult = {

    val res = new ListBuffer[ElementUsage]()

    val scope = new LocalSearchScope(ctx.element.getContainingFile)

    var didExitBeforeExhaustion = false

    val elementsForSearch = ctx.element match {
      case enumCase: ScEnumCase =>
        val syntheticMembers = ScalaPsiUtil.getCompanionModule(enumCase.enumParent).toSeq.flatMap(_.membersWithSynthetic).collect {
          case n: ScNamedElement if ScalaUsageNamesUtil.enumSyntheticMethodNames.contains(n.name) => n
        }
        enumCase.getSyntheticCounterpart +: syntheticMembers
      case e: ScNamedElement => Seq(e)
    }

    val refProcessor = new Processor[PsiReference] {
      override def process(ref: PsiReference): Boolean = {

        val usage = ElementUsageWithReference(ref.getElement, ctx.element)

        res.addOne(usage)

        val continue = !ctx.canExit(usage)

        if (!continue) {
          didExitBeforeExhaustion = true
        }

        continue
      }
    }

    elementsForSearch.foreach(ReferencesSearch.search(_, scope).forEach(refProcessor))

    new SearchMethodResult(res.toSeq, didExitBeforeExhaustion)
  }
}