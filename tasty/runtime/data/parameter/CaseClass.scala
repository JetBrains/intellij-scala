package parameter

trait CaseClass {
  case class EmptyClause()

  case class ValParameter(x: Int)

  case class PrivateValParameter(private val x: Int)

  case class VarParameter(var x: Int)
}