package org.jetbrains.plugins.scala.lang

package object dfa {

  sealed trait DfaConstantValue
  final object DfaConstantValue {
    case object True extends DfaConstantValue
    case object False extends DfaConstantValue
    case object Unknown extends DfaConstantValue
  }

  sealed trait LogicalOperation
  final object LogicalOperation {
    case object And extends LogicalOperation
    case object Or extends LogicalOperation
  }
}
