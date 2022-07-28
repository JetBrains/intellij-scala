package org.jetbrains.plugins.scala.lang.completion.weighter

import com.intellij.codeInsight.completion.{CompletionLocation, CompletionWeigher}
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.psi.PsiMethod
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.completion.lookups.ScalaLookupItem
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction

class ScalaMethodCompletionWeigher extends CompletionWeigher {
  case class MethodNameComparable(name: String, hasParameters: Boolean) extends Comparable[MethodNameComparable] {
    override def compareTo(o: MethodNameComparable): Int = {
      val i = name.compareTo(o.name)
      if (i != 0) return 0
      if (hasParameters == o.hasParameters) 0
      else if (hasParameters && !o.hasParameters) 1
      else -1
    }
  }



  override def weigh(element: LookupElement, location: CompletionLocation): Comparable[_] = element match {
    case ScalaLookupItem(_, namedElement) =>
      namedElement match {
        case psi: ScFunction =>
          MethodNameComparable(psi.name, psi.parameters.nonEmpty)
        case psi: PsiMethod =>
          MethodNameComparable(psi.name, psi.getParameterList.getParametersCount > 0)
        case _ => null
      }
    case _ => null //do not compare to anything in Java
  }
}