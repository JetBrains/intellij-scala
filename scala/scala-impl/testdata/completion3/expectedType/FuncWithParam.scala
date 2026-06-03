class FuncWithParam { //SCL-8463

  type One = Int
  val kurumba: One = 1
  val karamba: String = ""

  def foo(t: One) = t + 1

  foo(k<caret>)
}
