package org.jetbrains.plugins.scala.lang.psi.implicits

import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.lang.psi.api.ImplicitArgumentsOwner
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScNewTemplateDefinition
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveResult

object ImplicitArgumentsUtil {
  def implicitArgumentsFor(element: PsiElement): Option[Seq[ScalaResolveResult]] = {
    val owner = element match {
      case n: ScNewTemplateDefinition    => n.constructor
      case owner: ImplicitArgumentsOwner => Some(owner)
      case _                             => None
    }
    owner.flatMap(_.findImplicitArguments)
  }

}
