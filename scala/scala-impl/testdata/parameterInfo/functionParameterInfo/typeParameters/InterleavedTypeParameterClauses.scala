def foo[A](first: A)[B](second: B): B = second

foo(1)(<caret>"value")
//TEXT: [A](first: A)[B](second: B), STRIKEOUT: false