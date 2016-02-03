package org.jetbrains.plugins.scala.codeInsight.intention.expression

import org.jetbrains.plugins.scala.codeInsight.intention.InspectionBasedIntention
import org.jetbrains.plugins.scala.codeInspection.expression.ConstantExpressionInspection

class SimplifyExpressionIntention
  extends InspectionBasedIntention("Simplify expression",
    "Simplify", new ConstantExpressionInspection())