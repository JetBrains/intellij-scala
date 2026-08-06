object SCL5033 {
  class A
  class B

  class C {
    def test(a: A) { foo(/*start*/a/*end*/) }
    def foo(b: B) {}

    implicit def convert(a: A) = new B
  }
}
//SCL5033.A