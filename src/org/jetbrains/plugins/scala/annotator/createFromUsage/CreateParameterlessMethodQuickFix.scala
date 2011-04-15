package org.jetbrains.plugins.scala.annotator.createFromUsage

import org.jetbrains.plugins.scala.lang.psi.api.expr.ScReferenceExpression

/**
 * Pavel Fatin
 */

class CreateParameterlessMethodQuickFix(ref: ScReferenceExpression)
        extends CreatePropertyQuickFix(ref, "parameterless method", "def")

class CreateVariableQuickFix(ref: ScReferenceExpression)
        extends CreatePropertyQuickFix(ref, "variable", "var")

class CreateValueQuickFix(ref: ScReferenceExpression)
        extends CreatePropertyQuickFix(ref, "value", "val")
