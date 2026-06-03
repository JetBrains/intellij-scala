val x: (Int, Int) => Int = _ + _

x(v2 = 33<caret>)
//TEXT: [v2: Int], [v1: Int], STRIKEOUT: false