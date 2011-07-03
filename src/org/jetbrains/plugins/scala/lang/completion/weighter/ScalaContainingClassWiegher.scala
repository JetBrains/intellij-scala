package org.jetbrains.plugins.scala.lang.completion.weighter

import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.completion.{CompletionLocation, CompletionWeigher}
import org.jetbrains.plugins.scala.lang.resolve.ResolveUtils
/**
 * @author Alexander Podkhalyuzin
 */

class ScalaContainingClassWiegher extends CompletionWeigher {
  def weigh(element: LookupElement, location: CompletionLocation): Comparable[_] = {
    val isBold = element.getUserData(ResolveUtils.isBoldKey) match {
      case null => false
      case v => v.booleanValue()
    }

    val isUnderlined = element.getUserData(ResolveUtils.isUnderlinedKey) match {
      case null => false
      case v => v.booleanValue()
    }
    if (isBold) new Integer(0)
    else if (isUnderlined) new Integer(2)
    else new Integer(1)
  }
}