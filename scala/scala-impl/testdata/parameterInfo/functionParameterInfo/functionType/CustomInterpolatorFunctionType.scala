implicit class CustomInterpolator(private val stringContext: StringContext) {
  def test(values: Any*)(i: Int): Unit = {}
}

test""(<caret>)
//TEXT: v1: Int, STRIKEOUT: false