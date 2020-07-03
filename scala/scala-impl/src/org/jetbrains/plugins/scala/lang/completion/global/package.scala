package org.jetbrains.plugins.scala
package lang
package completion

import org.jetbrains.plugins.scala.lang.psi.api.ScalaPsiElement
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef._

package object global {

  private[completion] object ClassOrTrait {

    def unapply(element: ScalaPsiElement): Option[ScConstructorOwner] = element match {
      case `class`: ScClass => Some(`class`)
      case `trait`: ScTrait => Some(`trait`)
      case _ => None
    }
  }

  private[global] object CompanionModule {

    import extensions.OptionExt

    // todo ScalaPsiUtil.getCompanionModule / fakeCompanionModule
    def unapply(definition: ScConstructorOwner): Option[ScObject] =
      definition.baseCompanionModule.filterByType[ScObject]
  }
}
