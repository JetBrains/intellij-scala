class A { def a = ??? }

class Wrapper[T]

class Test {

  implicit val wrapperForA: Wrapper[A] = ???

  implicit def conv[T](v: Int)(implicit ev: Wrapper[T]): T = ???

  0.<ref>a
}