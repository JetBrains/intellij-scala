package org.jetbrains.plugins.scala.lang.psi.api.base

import com.intellij.psi._
import api.toplevel._
import statements._
import psi.types.Nothing

/**
* @author ilyas
*/

//wrapper over identifier for variable declarations 'var v : T' 
trait ScFieldId extends ScTyped {
  def calcType = getParent/*id list*/.getParent match {
    case typed : ScTypedDeclaration => typed.calcType
    //partial matching
  }
}