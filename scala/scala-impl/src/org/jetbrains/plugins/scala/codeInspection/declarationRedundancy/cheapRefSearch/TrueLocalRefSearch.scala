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

private[cheapRefSearch] final class TrueLocalRefSearch(override val shouldProcess: ShouldProcess) extends Method {

  private val IgnoreAccessScope = true

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

        val usage = ElementUsageWithKnownReference(ref.getElement, ctx.element)

        res.addOne(usage)

        val continue = !ctx.canExit(usage)

        if (!continue) {
          didExitBeforeExhaustion = true
        }

        continue
      }
    }

    /**
     * We ignore access scope specifically because of SCL-20114.
     *
     * Consider the below code:
     *
     * package foo
     * class A { private[foo] def bar() {}; bar() }
     *
     * Normally you'd place A.scala in
     * src/main/scala/foo/ and LocalRefSearch would not need to pass
     * ignoreAccessScope = true into ReferencesSearch.search.
     *
     * But if you place A.scala anywhere else, a discrepancy between directory
     * structure and package declaration appears. For the Scala compiler
     * this is not an issue; it will resolve this discrepancy in the
     * target directory by creating a directory structure that maps to
     * package declarations. So placing A.scala for example in src/main/scala,
     * is a valid thing to do, and the Scala plugin must be able to deal with
     * these files correctly as well.
     *
     * See [[org.jetbrains.plugins.scala.lang.psi.impl.ScalaUseScope.fromQualifiedPrivate]],
     * which is responsible for narrowing the use scope. It is exactly that narrowing
     * that we are ignoring by passing this flag.
     */

    elementsForSearch.foreach(ReferencesSearch.search(_, scope, IgnoreAccessScope).forEach(refProcessor))

    new SearchMethodResult(res.toSeq, didExitBeforeExhaustion)
  }
}