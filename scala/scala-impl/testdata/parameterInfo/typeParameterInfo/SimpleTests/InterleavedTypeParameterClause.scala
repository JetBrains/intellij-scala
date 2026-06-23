def foo[A](first: A)[B](second: B): B = second

foo[Int](1)[<caret>]("value")
//TEXT: B, STRIKEOUT: false