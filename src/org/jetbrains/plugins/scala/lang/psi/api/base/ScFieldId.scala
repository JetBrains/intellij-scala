package org.jetbrains.plugins.scala
package lang
package psi
package api
package base

import com.intellij.psi._
import api.toplevel._
import statements._
import psi.types.Nothing

/**
* @author ilyas
*/

//wrapper over identifier for variable declarations 'var v : T' 
trait ScFieldId extends ScTypedDefinition {
  def calcType = getParent/*id list*/.getParent match {
    case typed : ScTypedDeclaration => typed.calcType
    //partial matching
  }
}