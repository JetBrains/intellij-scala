package org.jetbrains.plugins.scala.lang.completion.weighter

import com.intellij.codeInsight.completion.{CompletionLocation, CompletionWeigher}
import com.intellij.codeInsight.lookup.LookupElement
import org.jetbrains.plugins.scala.lang.completion.lookups.ScalaLookupItem

/**
 * @author Alexander Podkhalyuzin
 */

class ScalaContainingClassWiegher extends CompletionWeigher {
  def weigh(element: LookupElement, location: CompletionLocation): Comparable[_] = {
    ScalaLookupItem.original(element) match {
      case element: ScalaLookupItem =>
        if (element.bold) new Integer(2)
        else if (element.isUnderlined) new Integer(0)
        else new Integer(1)
      case _ => new Integer(1)
    }
  }
}