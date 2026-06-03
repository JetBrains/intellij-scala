class A {
  def foo(x: Int): Int = x
}

object Wrapper {
  val a = new A
  val thisisint = 45

  a foo th/*caret*/
}
//thisisint