package org.jetbrains.plugins.scala.lang.transformation.annotations

import org.jetbrains.plugins.scala.extensions.{&&, Parent, PsiElementExt}
import org.jetbrains.plugins.scala.lang.psi.api.Typed
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScReferencePattern
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScVariableDefinition
import org.jetbrains.plugins.scala.lang.transformation._

/**
  * @author Pavel Fatin
  */
object AddTypeToVariableDefinition extends AbstractTransformer {
  def transformation = {
    case (p: ScReferencePattern) && Parent(l @ Parent(_: ScVariableDefinition)) && Typed(t)
      if !l.nextSibling.exists(_.getText == ":") =>

      appendTypeAnnotation(l, t)
  }
}
