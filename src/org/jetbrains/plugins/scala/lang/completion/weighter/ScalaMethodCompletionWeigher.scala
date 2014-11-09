package org.jetbrains.plugins.scala.lang.completion.weighter

import com.intellij.codeInsight.completion.{CompletionLocation, CompletionWeigher}
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.psi.PsiMethod
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.completion.lookups.ScalaLookupItem
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction

/**
 * @author Alexander Podkhalyuzin
 */
class ScalaMethodCompletionWeigher extends CompletionWeigher {
  case class MethodNameComparable(name: String, hasParameters: Boolean) extends Comparable[MethodNameComparable] {
    def compareTo(o: MethodNameComparable): Int = {
      val i = name.compareTo(o.name)
      if (i != 0) return 0
      if (hasParameters == o.hasParameters) 0
      else if (hasParameters && !o.hasParameters) 1
      else -1
    }
  }



  def weigh(element: LookupElement, location: CompletionLocation): Comparable[_] = {
    val obj = element match {
      case s: ScalaLookupItem => s.element
      case _ => return null //do not compare to anything in Java
    }
    obj match {
      case psi: ScFunction =>
        MethodNameComparable(psi.name, psi.parameters.length > 0)
      case psi: PsiMethod =>
        MethodNameComparable(psi.name, psi.getParameterList.getParametersCount > 0)
      case _ => null
    }
  }
}