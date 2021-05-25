package typeDefinition

trait CaseClass() {
  case class EmptyClause()

  case class ValParameter(val x: Int)

  case class VarParameter(var x: Int)
}