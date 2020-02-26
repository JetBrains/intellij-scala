package org.jetbrains.plugins.scala.annotator.createFromUsage

import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScReferenceExpression

class CreateParameterlessMethodQuickFix(ref: ScReferenceExpression)
  extends CreateEntityQuickFix(ref, "def") {

  override val getText: String = ScalaBundle.message("create.parameterless.method.named", ref.nameId.getText)
  override val getFamilyName: String = ScalaBundle.message("family.name.create.parameterless.method")
}

class CreateVariableQuickFix(ref: ScReferenceExpression)
  extends CreateEntityQuickFix(ref, "var") {

  override val getText: String = ScalaBundle.message("create.variable.named", ref.nameId.getText)
  override val getFamilyName: String = ScalaBundle.message("family.name.create.variable")
}


class CreateValueQuickFix(ref: ScReferenceExpression)
  extends CreateEntityQuickFix(ref, "val") {

  override val getText: String = ScalaBundle.message("create.value.named", ref.nameId.getText)
  override val getFamilyName: String = ScalaBundle.message("family.name.create.value")
}


class CreateMethodQuickFix(ref: ScReferenceExpression)
  extends CreateEntityQuickFix(ref, "def") {

  override val getText: String = ScalaBundle.message("create.method.named", ref.nameId.getText)
  override val getFamilyName: String = ScalaBundle.message("family.name.create.method")
}

