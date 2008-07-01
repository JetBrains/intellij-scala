package org.jetbrains.plugins.scala.lang.refactoring.introduceVariable.typeManipulator

import psi.types.ScType

/**
* User: Alexander Podkhalyuzin
* Date: 01.07.2008
*/

object TypeManipulator {
  def wrapType(typez: ScType): IType = {
    return new SIType {
      val t = typez
      def getScType = t
    }
  }
  def unwrapType(iType: IType): ScType = {
    iType match {
      case x: SIType => x.getScType
      case _ => null
    }
  }
}