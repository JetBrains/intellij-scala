package org.jetbrains.plugins.scala.lang.completion.weighter

import com.intellij.codeInsight.completion.{CompletionLocation, CompletionWeigher}
import com.intellij.codeInsight.lookup.LookupElement
import org.jetbrains.plugins.scala.lang.completion.lookups.ScalaLookupItem
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction

/**
  * @author Alexander Podkhalyuzin
  */

class ScalaContainingClassWiegher extends CompletionWeigher {
  def weigh(element: LookupElement, location: CompletionLocation): Comparable[_] = {
    import KindWeights._
    ScalaLookupItem.original(element) match {
      case si: ScalaLookupItem if si.isLocalVariable => local
      case si: ScalaLookupItem if si.isUnderlined => underlined
      case si: ScalaLookupItem if si.isDeprecated => deprecated
      case p: ScalaLookupItem if p.isNamedParameter => nparam
      case si: ScalaLookupItem if si.bold => bold
      case si: ScalaLookupItem =>
        si.element match {
          case func: ScFunction if func.getContainingClass == null => localFunc
          case _ => normal
        }
      case _ => normal
    }
  }

  object KindWeights extends Enumeration {
    val deprecated, underlined, normal, nparam, bold, localFunc, local = Value
  }

}