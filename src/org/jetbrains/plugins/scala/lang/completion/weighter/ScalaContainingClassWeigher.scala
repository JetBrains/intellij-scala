package org.jetbrains.plugins.scala.lang.completion.weighter

import com.intellij.codeInsight.completion.{CompletionLocation, CompletionWeigher}
import com.intellij.codeInsight.lookup.LookupElement
import org.jetbrains.plugins.scala.lang.completion.lookups.ScalaLookupItem
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScModifierListOwner

/**
  * @author Alexander Podkhalyuzin
  */

class ScalaContainingClassWeigher extends CompletionWeigher {
  def weigh(element: LookupElement, location: CompletionLocation): Comparable[_] = {
    import KindWeights._
    ScalaLookupItem.original(element) match {
      case si: ScalaLookupItem if si.isLocalVariable => local
      case si: ScalaLookupItem if si.isUnderlined => underlined
      case si: ScalaLookupItem if si.isDeprecated => deprecated
      case p: ScalaLookupItem if p.isNamedParameter => nparam
      case sii: ScalaLookupItem if sii.bold => bold
      case si: ScalaLookupItem =>
        si.element match {
          case func: ScFunction if func.getContainingClass == null => localFunc
          case withImplicit: ScModifierListOwner if withImplicit.hasModifierPropertyScala("implicit") => underlined
          case _ => normal
        }
      case _ => normal
    }
  }

  object KindWeights extends Enumeration {
    val deprecated, underlined, normal, nparam, bold, localFunc, local = Value
  }

}