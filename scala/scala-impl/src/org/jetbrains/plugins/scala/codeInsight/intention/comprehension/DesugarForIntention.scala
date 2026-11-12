package org.jetbrains.plugins.scala.codeInsight.intention.comprehension

import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.codeInsight.intention.AbstractIntention
import org.jetbrains.plugins.scala.extensions.*
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScFor

class DesugarForIntention extends AbstractIntention(ScalaBundle.message("desugar.for.comprehension"), ScalaBundle.message("family.name.convert.to.desugared.expression"))(
  (_, _) => {
    case Parent(statement: ScFor) => statement.desugared(forDisplay = true).foreach { desugared =>
      statement.replace(desugared)
    }
  }
)
