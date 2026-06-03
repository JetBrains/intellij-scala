package parameter

trait CaseClass {
  case class EmptyClause()

  case class ValParameter1(/**/val /**/x: Int)

  case class ValParameter2(x: Int)(val y: Int)

  case class ValParameter3(x1: Int, x2: Int)(val y1: Int, val y2: Int)

  case class PrivateValParameter(/**/private val /**/x: Int)

  case class ProtectedValParameter(protected val x: Int)

  case class VarParameter(var x: Int)
}