sealed abstract class CtorType[-P]
case class Hello[-P]() extends CtorType[P] {
  def hello(p: P) = 123
}

trait Component[-P, CT[-p] <: CtorType[p]] {
  val ctor: CT[P]
}

implicit def toCtorOps[P, CT[-p] <: CtorType[p]](base: Component[P, CT]) =
  base.ctor

val example: Component[Int, Hello] = ???
example.ctor.hello(123)
val left: Int = example.hello(123)
//true