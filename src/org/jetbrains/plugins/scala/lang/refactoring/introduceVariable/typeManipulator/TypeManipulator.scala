package org.jetbrains.plugins.scala.lang.refactoring.introduceVariable.typeManipulator

import psi.types.ScType

/**
* User: Alexander Podkhalyuzin
* Date: 01.07.2008
*/

object TypeManipulator {
  def wrapType(typez: ScType): IType = {
    return new SIType {
      private val t = typez
      def getScType = t
      private val name = typez match {
        case null => null
        case _ => ScType.presentableText(typez)
      }
      def getName = name
    }
  }
  def unwrapType(iType: IType): ScType = {
    iType match {
      case x: SIType => x.getScType
      case _ => null
    }
  }
}