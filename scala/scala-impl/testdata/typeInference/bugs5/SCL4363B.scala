implicit val x = 123

class A
object A {
  implicit def foo(implicit k: Int): (A, B, Int) = (new A, new B, k)
}

class B
object B {
  implicit def k : Int = 1
  implicit def z: (A, B, String) = (new A, new B, "")
}

def goo[T](implicit pair: (A, B, T)): T = pair._3

/*start*/goo/*end*/
//Nothing