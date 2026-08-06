object ApplyCurrings {
  def apply(x: Int)(y: Int) = 2
}

ApplyCurrings(1)(<caret>)
//TEXT: (x: Int)(y: Int), STRIKEOUT: false