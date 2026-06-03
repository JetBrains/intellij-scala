package org.jetbrains.plugins.scala.codeInsight.intention.comprehension

import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.codeInsight.intention.AbstractIntention
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScFor
import org.jetbrains.plugins.scala.lang.transformation.Transformer
import org.jetbrains.plugins.scala.lang.transformation.general.ExpandForComprehension

class DesugarForIntention extends AbstractIntention(ScalaBundle.message("desugar.for.comprehension"), ScalaBundle.message("family.name.convert.to.desugared.expression"))(
  (_, _) => {
    case Parent(statement: ScFor) =>
      Transformer.applyTransformerAndReformat(statement, statement.getContainingFile, new ExpandForComprehension())
  }
)
