class AAA {
  class T
  case class CaseClass
}

object bbb {
  val a : AAA
  import a.{T => _, _}

  def foo: Case<ref>Class

}