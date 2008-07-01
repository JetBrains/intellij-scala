package org.jetbrains.plugins.scala.lang.refactoring.introduceVariable.typeManipulator

import psi.types.ScType

/** 
* User: Alexander Podkhalyuzin
* Date: 01.07.2008
*/

trait SIType extends IType {
  def getScType: ScType
}