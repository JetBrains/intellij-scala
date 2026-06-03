class Ref[T]
abstract class Outer {type T}
val x: Ref[_ <: java.lang.Number] = null
val y: Ref[T] forSome {type T <: java.lang.Number} = x
//True