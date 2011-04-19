package org.jetbrains.plugins.scala.annotator.createFromUsage

import org.jetbrains.plugins.scala.lang.psi.api.expr.ScReferenceExpression

/**
 * Pavel Fatin
 */

class CreateParameterlessMethodQuickFix(ref: ScReferenceExpression)
        extends CreateEntityQuickFix(ref, "parameterless method", "def")

class CreateVariableQuickFix(ref: ScReferenceExpression)
        extends CreateEntityQuickFix(ref, "variable", "var")

class CreateValueQuickFix(ref: ScReferenceExpression)
        extends CreateEntityQuickFix(ref, "value", "val")

class CreateMethodQuickFix(ref: ScReferenceExpression)
        extends CreateEntityQuickFix(ref, "method", "def")
