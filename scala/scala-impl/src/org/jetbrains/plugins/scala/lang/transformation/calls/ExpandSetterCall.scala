package org.jetbrains.plugins.scala.lang.transformation
package calls

import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.extensions.{&&, Parent, ReferenceTarget}
import org.jetbrains.plugins.scala.lang.psi.api.base.ScFieldId
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScReferencePattern
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScAssignment
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScVariable
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.ScTemplateBody
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaCode._
import org.jetbrains.plugins.scala.project.ProjectContext

/**
  * @author Pavel Fatin
  */
class ExpandSetterCall extends AbstractTransformer {
  protected def transformation(implicit project: ProjectContext): PartialFunction[PsiElement, Unit] = {
    case e @ ScAssignment(l @ ReferenceTarget((_: ScReferencePattern | _: ScFieldId) &&
      Parent(Parent((v: ScVariable) && Parent(_: ScTemplateBody)))), r)
      if !v.getModifierList.accessModifier.exists(it => it.isPrivate && it.isThis)=>

      e.replace(code"${l.getText + "_="}($r)")
  }
}
