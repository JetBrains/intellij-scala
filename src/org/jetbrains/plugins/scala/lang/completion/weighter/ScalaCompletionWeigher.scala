package org.jetbrains.plugins.scala.lang.completion.weighter

import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.completion.{CompletionLocation, CompletionWeigher}
import org.jetbrains.plugins.scala.lang.resolve.ResolveUtils.ScalaLookupObject
import com.intellij.psi.PsiMethod
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScParameter
import org.jetbrains.plugins.scala.lang.completion.ScalaCompletionUtil

/**
 * @author Alexander Podkhalyuzin
 */

class ScalaCompletionWeigher extends CompletionWeigher {
  case class MethodNameComparable(name: String, hasParameters: Boolean) extends Comparable[MethodNameComparable] {
    def compareTo(o: MethodNameComparable): Int = {
      val i = name.compareTo(o.name)
      if (i != 0) return 0
      if (hasParameters == o.hasParameters) return 0
      else if (hasParameters && !o.hasParameters) return 1
      else return -1
    }
  }



  def weigh(element: LookupElement, location: CompletionLocation): Comparable[_] = {
    val obj = element.getObject
    obj match {
      case psi: ScFunction =>
        MethodNameComparable(psi.getName, psi.parameters.length > 0)
      case psi: PsiMethod =>
        MethodNameComparable(psi.getName, psi.getParameterList.getParametersCount > 0)
      case _ => null
    }
  }
}