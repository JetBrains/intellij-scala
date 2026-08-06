class Ref[T]
abstract class Outer {type T}
trait D {
  val x: Ref[(_ <: Outer with Singleton)#T]
  val y: Ref[x_type # T] forSome {type x_type <: Outer with Singleton} = x
}