class Foo[T] {
  def m[U](f: T => U)(implicit x: DummyImplicit) = println("U")
  def m[U](f: T => List[U]) = println("List[U]")
}
class A {
  val foo = new Foo[String]
  foo.m(_ => 42)
  foo.<ref>m(_ => List(42))
}