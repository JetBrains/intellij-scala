package org.jetbrains.plugins.scala.lang

package object dfa {

  sealed trait DfaConstantValue
  object DfaConstantValue {
    case object True extends DfaConstantValue
    case object False extends DfaConstantValue
    case object Unknown extends DfaConstantValue
  }
}
