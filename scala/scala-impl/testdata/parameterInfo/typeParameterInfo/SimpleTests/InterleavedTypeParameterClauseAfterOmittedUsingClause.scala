given Int = 1
def foo(first: Int)(using Int)[A](second: A): A = second

foo(1)[<caret>](2)
//TEXT: A, STRIKEOUT: false