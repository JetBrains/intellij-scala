class A {
  def foo(x: Int)(implicit y: Int) = x + y
}
implicit val x: Int = 45
val zz = new A
val a: Int = zz foo 3
//True