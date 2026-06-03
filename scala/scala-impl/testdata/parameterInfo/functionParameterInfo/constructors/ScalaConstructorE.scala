trait TileMergeMethods[T]
class Pyramid[V: ? => TileMergeMethods[V]]()

new Pyramid[Int]()(<caret>)
//TEXT: [V: Any => TileMergeMethods[Int]]()(implicit `?=>TileMergeMethods[V]$V$0`: () => Int), STRIKEOUT: false