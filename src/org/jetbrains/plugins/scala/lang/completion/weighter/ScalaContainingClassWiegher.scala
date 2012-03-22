package org.jetbrains.plugins.scala.lang.completion.weighter

import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.completion.{CompletionLocation, CompletionWeigher}
import org.jetbrains.plugins.scala.lang.resolve.ResolveUtils
import org.jetbrains.plugins.scala.lang.completion.lookups.ScalaLookupItem

/**
 * @author Alexander Podkhalyuzin
 */

class ScalaContainingClassWiegher extends CompletionWeigher {
  def weigh(element: LookupElement, location: CompletionLocation): Comparable[_] = {
    element match {
      case element: ScalaLookupItem =>
        if (element.isBold) new Integer(0)
        else if (element.isUnderlined) new Integer(2)
        else new Integer(1)
      case _ => new Integer(1)
    }
  }
}