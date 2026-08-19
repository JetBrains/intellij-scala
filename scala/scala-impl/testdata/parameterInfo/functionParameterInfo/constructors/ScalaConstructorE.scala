trait TileMergeMethods[T]
class Pyramid[V: ? => TileMergeMethods[V]]()

new Pyramid[Int]()(<caret>)
//TEXT: [V: Function1[_, TileMergeMethods[Int]]]()(implicit `?=>TileMergeMethods[V]$V$0`: () => Int), STRIKEOUT: false