package org.jetbrains.plugins.scala.lang.completion.weighter

import com.intellij.codeInsight.completion.{CompletionLocation, CompletionWeigher}
import com.intellij.codeInsight.lookup.LookupElement
import org.jetbrains.plugins.scala.lang.completion.lookups.ScalaLookupItem
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScModifierListOwner

class ScalaContainingClassWeigher extends CompletionWeigher {
  override def weigh(element: LookupElement, location: CompletionLocation): Comparable[_] = {
    import KindWeights._
    element match {
      case ScalaLookupItem(item, namedElement) =>
        namedElement match {
          case _ if item.isLocalVariable => local
          case _ if item.isUnderlined => underlined
          case _ if item.isNamedParameter => nparam
          case _ if item.bold => bold
          case func: ScFunction if func.getContainingClass == null => localFunc
          case withImplicit: ScModifierListOwner if withImplicit.hasModifierPropertyScala("implicit") => underlined
          case _ => normal
        }
      case _ => normal
    }
  }

  object KindWeights extends Enumeration {
    val underlined, normal, nparam, bold, localFunc, local = Value
  }

}