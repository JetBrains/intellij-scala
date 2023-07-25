def foo(@deprecated x: Int = 45) = x

foo(<caret>)
//TEXT: @deprecated x: Int = 45, STRIKEOUT: false