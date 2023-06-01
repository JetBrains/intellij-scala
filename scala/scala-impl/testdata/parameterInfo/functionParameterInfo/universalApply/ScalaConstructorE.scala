trait TileMergeMethods[T]
class Pyramid[V: ? => TileMergeMethods[V]]()

Pyramid[Int]()(<caret>)
// [V: Function1[?, TileMergeMethods[Int]]]()(implicit `?=>TileMergeMethods[V]$V$0`: () => Int)