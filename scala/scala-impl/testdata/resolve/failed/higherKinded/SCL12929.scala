trait A {
  val x: Int
}

trait B {
  val y: Int
}

class C[T[_] <: A with B](a: T[Int]){
  val b = a.<ref>x
}