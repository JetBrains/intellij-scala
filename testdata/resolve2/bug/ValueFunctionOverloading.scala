object ValueFunctionOverloading {
  def foo(x: Int): Int = x + 1
  val foo: String => String = "" + _

  /* line: 2 */ foo(3)
  /* line: 3 */ foo("")
}