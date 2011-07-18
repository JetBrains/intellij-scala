class A

class B

implicit def a2b(a: A): B = new B

trait M[X]

object M {
  def apply[Y](y: Y): M[Y] = null
}

val ma1: M[B] = M(new A) // okay

val MV = M
val ma2: M[B] = /*start*/ MV(new A) /*end*/
// M[B]