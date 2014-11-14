package org.jetbrains.plugins.scala.lang.completion.weighter

import com.intellij.codeInsight.completion.{CompletionLocation, CompletionWeigher}
import com.intellij.codeInsight.lookup.LookupElement
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
    ScalaLookupItem.original(element) match {
      case item: ScalaLookupItem => ParameterNameComparable(item.isNamedParameter)
      case _ => ParameterNameComparable(isNamedParameters = false)
    }
  }
}