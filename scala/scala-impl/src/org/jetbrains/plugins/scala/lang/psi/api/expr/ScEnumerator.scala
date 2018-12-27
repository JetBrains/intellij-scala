package org.jetbrains.plugins.scala.lang.psi.api.expr

import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElement

trait ScEnumerator extends ScalaPsiElement {
  def analog: Option[ScEnumerator.Analog] = this.parentOfType(classOf[ScForStatement]) flatMap {
    _.getDesugaredEnumeratorAnalog(this)
  }
}

object ScEnumerator {
  /*
    Analog maps enumerators to their desugared counterparts (which are method calls). For example:

      for { i <- List(1); i2 = i if i2 > 0; i3 = i2; i4 <- List(i3) } yield i4
            |----g1----|  |-d2-| |--if3--|  |-d4--|  |-----g5-----|
    maps to

      List(1).map { i => val i2 = i; (i, i2) }.withFilter { case (i, i2) => i2 > 0 }.flatMap { case (i, i2) => val i3 = i2; List(i3).map(i4 => i4) }
      |-----------------------------------g1:analogMethodCall--------------------------------------------------------------------------------------|
      |---------d2:analogMethodCall----------|
      |----------------------------if3:analogMethodCall----------------------------|
                                                                                                                            |-----g5:analogMC----|

    Note that d4 does not have an analogMethodCall
   */

  case class Analog(analogMethodCall: ScMethodCall)
}