package org.jetbrains.plugins.scala.annotator.createFromUsage

import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScReferenceExpression

final class CreateParameterlessMethodQuickFix(ref: ScReferenceExpression)
  extends CreateEntityQuickFix(ref, "def") {

  override val getText: String = ScalaBundle.message("create.parameterless.method.named", ref.nameId.getText)
  override val getFamilyName: String = ScalaBundle.message("family.name.create.parameterless.method")

  override protected def withRef(newRef: ScReferenceExpression): CreateEntityQuickFix =
    new CreateParameterlessMethodQuickFix(newRef)
}

final class CreateVariableQuickFix(ref: ScReferenceExpression)
  extends CreateEntityQuickFix(ref, "var") {

  override val getText: String = ScalaBundle.message("create.variable.named", ref.nameId.getText)
  override val getFamilyName: String = ScalaBundle.message("family.name.create.variable")

  override protected def withRef(newRef: ScReferenceExpression): CreateEntityQuickFix =
    new CreateVariableQuickFix(newRef)
}


final class CreateValueQuickFix(ref: ScReferenceExpression)
  extends CreateEntityQuickFix(ref, "val") {

  override val getText: String = ScalaBundle.message("create.value.named", ref.nameId.getText)
  override val getFamilyName: String = ScalaBundle.message("family.name.create.value")

  override protected def withRef(newRef: ScReferenceExpression): CreateEntityQuickFix =
    new CreateValueQuickFix(newRef)
}


final class CreateMethodQuickFix(ref: ScReferenceExpression)
  extends CreateEntityQuickFix(ref, "def") {

  override val getText: String = ScalaBundle.message("create.method.named", ref.nameId.getText)
  override val getFamilyName: String = ScalaBundle.message("family.name.create.method")

  override protected def withRef(newRef: ScReferenceExpression): CreateEntityQuickFix =
    new CreateMethodQuickFix(newRef)
}
