trait TileMergeMethods[T]
class Pyramid[V: ? => TileMergeMethods[V]]()

new Pyramid[Int]()(<caret>)
// [V : Any => TileMergeMethods[Int]]()(implicit `?=>TileMergeMethods[V]$V$0`: () => Int)