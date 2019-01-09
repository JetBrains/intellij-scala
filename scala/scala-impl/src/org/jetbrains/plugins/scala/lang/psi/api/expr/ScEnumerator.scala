package org.jetbrains.plugins.scala.lang.psi.api.expr

import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.parser.ScalaElementType
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElement
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScCaseClauses

trait ScEnumerator extends ScalaPsiElement {
  def forStatement: Option[ScForStatement]

  def analog: Option[ScEnumerator.Analog]

  // the token that marks the enumerator (<-, =, if)
  def enumeratorToken: PsiElement
}

object ScEnumerator {
  /*
    Analog maps enumerators to their desugared counterparts (which are method calls). For example:

      for { i <- List(1); i2 = i if i2 > 0; i3 = i2; i4 <- List(i3) } yield i4
            |----g1----|  |-d2-| |--if3--|  |-d4--|  |-----g5-----|
    maps to

      List(1).map { i => val i2 = i; (i, i2) }.withFilter { case (i, i2) => i2 > 0 }.flatMap { case (i, i2) => val i3 = i2; List(i3).map(i4 => i4) }
      |-----------------------------------g1:analogMethodCall--------------------------------------------------------------------------------------|
      |-----------------------g1:callExpr--------------------------------------------------|                   |-----g1:content------------------|

      |---------d2:analogMethodCall----------|
      |---------|        |----d2:content---|
      d2:callExpr

      |----------------------------if3:analogMethodCall----------------------------|
      |---------if3:callExpr----------------------------|                   |----|
                                                                          if3:content
                                                                                                                            |-----g5:analogMC----|

    Note that d4 does not have an analogMethodCall
   */

  case class Analog(analogMethodCall: ScMethodCall) {
    def callExpr: Option[ScReferenceExpression] =
      Option(analogMethodCall.getInvokedExpr).collect { case refExpr: ScReferenceExpression => refExpr }

    def content: Option[ScExpression] = {
      analogMethodCall
        .getLastChild
        .getLastChild
        .asInstanceOf[ScBlockExpr]
        .findLastChildByType[ScCaseClauses](ScalaElementType.CASE_CLAUSES)
        .getLastChild
        .lastChild collect { case block: ScBlock => block}
    }
  }
}