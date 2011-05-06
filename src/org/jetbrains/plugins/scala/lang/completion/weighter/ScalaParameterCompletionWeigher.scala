package org.jetbrains.plugins.scala.lang.completion.weighter

import com.intellij.codeInsight.lookup.LookupElement
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScParameter
import org.jetbrains.plugins.scala.lang.completion.ScalaCompletionUtil
import com.intellij.codeInsight.completion.{CompletionWeigher, CompletionLocation}

/**
 * @author Alexander Podkhalyuzin
 */

class ScalaParameterCompletionWeigher extends CompletionWeigher {
  case class ParameterNameComparable(isNamedParameters: Boolean)
    extends Comparable[ParameterNameComparable] {
    def compareTo(o: ParameterNameComparable): Int = {
      if (isNamedParameters == o.isNamedParameters) return 0
      else if (isNamedParameters && !o.isNamedParameters) return -1
      else return 1
    }
  }

  def weigh(element: LookupElement, location: CompletionLocation): Comparable[_] = {
    val obj = element.getObject
    obj match {
      case param: ScParameter =>
        val isNamed = ScalaCompletionUtil.getScalaLookupObject(element).isNamedParameter
        ParameterNameComparable(isNamed)
      case _ => null
    }
  }
}