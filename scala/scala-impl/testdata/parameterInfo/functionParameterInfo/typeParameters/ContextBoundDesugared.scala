def foo[A](x: Int)(implicit evidence$1: Ordering[A]): Unit = ()

foo(<caret>)
//TEXT: [A](x: Int)(implicit evidence$1: Ordering[A]), STRIKEOUT: false