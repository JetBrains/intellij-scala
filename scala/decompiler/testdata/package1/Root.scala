package package1

object Root {
  type T1 = _root_.package1.Members

  type T2 = _root_.package1.Members.type

  class Foo

  object Foo

  type T3 = _root_.package1.Root.Foo

  type T4 = _root_.package1.Root.Foo.type

  class C2 {
    class Foo

    object Foo

    type T1 = C2.this.Foo

    type T2 = C2.this.Foo.type

    type T3 = _root_.package1.Root.C2#Foo
  }

  type T5 = _root_.package1.Root.C2#Foo

  class C3[A] {
    type T1 = A
  }

  def method1[A](x: A): A = ???

  type T6[A] = A

  class Path1 {
    class Path2
  }

  def method2(x: _root_.package1.Root.Path1)(y: x.Path2): _root_.scala.Unit = ???

  val v: _root_.package1.Root.type = ???

  type T7 = _root_.package1.Root.v.Foo

  type T8 = _root_.package1.Root.v.Foo.type

  class C4 {
    val v: _root_.package1.Root.type = ???

    type T1 = C4.this.v.Foo

    type T2 = C4.this.v.Foo.type
  }
}