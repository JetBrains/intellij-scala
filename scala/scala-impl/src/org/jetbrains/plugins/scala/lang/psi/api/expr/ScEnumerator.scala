package org.jetbrains.plugins.scala.lang.psi.api.expr

import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.lang.psi.api.ScalaPsiElement

trait ScEnumerator extends ScalaPsiElement {
  def forStatement: Option[ScFor]

  def desugared: Option[ScEnumerator.DesugaredEnumerator]

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
  trait DesugaredEnumerator {
    def analogMethodCall: ScMethodCall

    def callExpr: Option[ScReferenceExpression]

    def content: Option[ScExpression]

    def generatorExpr: Option[ScExpression]
  }

  object withDesugared {
    def unapply(enum: ScEnumerator): Option[DesugaredEnumerator] = enum.desugared
  }
}