def foo[A](first: A)[B](second: B): B = second

foo(1)[<caret>]("value")
//TEXT: B, STRIKEOUT: false