case class A(x: Int, y: Int)
A(y = 4, x = 6<caret>)
//TEXT: [y: Int], [x: Int], STRIKEOUT: false