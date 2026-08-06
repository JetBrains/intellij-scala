trait TileMergeMethods[T]
class Pyramid[V: ? => TileMergeMethods[V]]()

Pyramid[Int]()(<caret>)
//TEXT: [V: Function1[?, TileMergeMethods[Int]]]()(using `?=>TileMergeMethods[V]$V$0`: () => Int), STRIKEOUT: false