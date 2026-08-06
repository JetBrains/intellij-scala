class B
class A {
  def +:::(x: B): Int = 45
}

object Wrapper {
  val xx: B = new B
  val a: A = new A

  x/*caret*/ +::: a
}
//xx