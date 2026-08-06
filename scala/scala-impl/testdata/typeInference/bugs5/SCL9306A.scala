object SCL9306B extends App {
  class A
  class B
  class C
  class D
  implicit def convert(f: A => B): (C => D) = { c: C => new D }

  def func1: (A => B) = { a: A => new B }

  /*start*/func1(new C)/*end*/
}
//SCL9306B.D