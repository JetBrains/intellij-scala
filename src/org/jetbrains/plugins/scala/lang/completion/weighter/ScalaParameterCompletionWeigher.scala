package org.jetbrains.plugins.scala.lang.completion.weighter

import com.intellij.codeInsight.lookup.LookupElement
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScParameter
import org.jetbrains.plugins.scala.lang.completion.ScalaCompletionUtil
import com.intellij.codeInsight.completion.{CompletionWeigher, CompletionLocation}
import org.jetbrains.plugins.scala.lang.completion.lookups.ScalaLookupItem

/**
 * @author Alexander Podkhalyuzin
 */

class ScalaParameterCompletionWeigher extends CompletionWeigher {
  case class ParameterNameComparable(isNamedParameters: Boolean)
    extends Comparable[ParameterNameComparable] {
    def compareTo(o: ParameterNameComparable): Int = {
      if (isNamedParameters == o.isNamedParameters) 0
      else if (isNamedParameters && !o.isNamedParameters) -1
      else 1
    }
  }

  def weigh(element: LookupElement, location: CompletionLocation): Comparable[_] = {
    element match {
      case item@ScalaLookupItem(param: ScParameter) => ParameterNameComparable(item.isNamedParameter)
      case _ => ParameterNameComparable(isNamedParameters = false)
    }
  }
}