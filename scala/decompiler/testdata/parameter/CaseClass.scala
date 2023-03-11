package parameter

trait CaseClass {
  case class EmptyClause()

  case class ValParameter(/**/val /**/x: Int)

  case class PrivateValParameter(/**/private val /**/x: Int)

  case class ProtectedValParameter(protected val x: Int)

  case class VarParameter(var x: Int)
}