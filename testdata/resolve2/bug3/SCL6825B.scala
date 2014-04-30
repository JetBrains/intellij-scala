object SCL6825B {
  class A {
    def foo = 1
  }

  class B {
    val size: Int = 144
  }
  implicit def z[T <: AnyRef {val size: Int}](t: T): A = new A
  implicit def g[T <: AnyRef {def size: Int}](t: T): A = new A

  val b = new B

  b./* */foo
}