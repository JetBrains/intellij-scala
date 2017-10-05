class Ref[T]
abstract class Outer {type T}
val x: Ref[(_ <: Outer with Singleton)#T] = null
val y: Ref[x_type # T] forSome {type x_type <: Outer with Singleton} = x
//True