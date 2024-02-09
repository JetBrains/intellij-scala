//> expected.error cannot.inline.notsimple.typealias
type My[T] = List[T]
val m: /*caret*/My[Int] = List(4, 5)