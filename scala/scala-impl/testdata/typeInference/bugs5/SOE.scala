class A[T]

class G[T]

class C extends A[D]

class B extends A[F]

class D extends G[D]

class F extends G[F]

val x = 1 match {
  case 1 => new C
  case 2 => new B
}

/*start*/x/*end*/
//A[_ >: D with F <: G[_ >: D with F]]