package org.jetbrains.plugins.scala.lang.transformation.annotations

import org.jetbrains.plugins.scala.extensions.{&&, Parent, PsiElementExt}
import org.jetbrains.plugins.scala.lang.psi.api.Typed
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScReferencePattern
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScPatternDefinition
import org.jetbrains.plugins.scala.lang.transformation._

/**
  * @author Pavel Fatin
  */
object AddTypeToValueDefinition extends AbstractTransformer {
  def transformation = {
    case (e: ScReferencePattern) && Parent(l @ Parent(_: ScPatternDefinition)) && Typed(t)
      if !l.nextSibling.exists(_.getText == ":") =>

      appendTypeAnnotation(l, t)
  }
}
