package parameter

trait Repeated {
  def method(xs: Int*): Unit

  class Class(xs: Int*)

  case class CaseClass(xs: Int*)
}