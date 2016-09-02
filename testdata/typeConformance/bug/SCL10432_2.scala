sealed abstract class CtorType[P]
case class Hello[P >: Int <: AnyVal]() extends CtorType[P] { //>: Int <: AnyVal
def hello(p: P) = 123
}

trait Component[P >: Int <: AnyVal, CT[p >: Int <: AnyVal] <: CtorType[p]] {
  val ctor: CT[P]
}

implicit def toCtorOps[P >: Int <: AnyVal, CT[p >: Int <: AnyVal] <: CtorType[p]](base: Component[P, CT]) =
  base.ctor

val example: Component[Int, Hello] = ???
example.ctor.hello(123)
val left: Int = example.hello(123)
//true