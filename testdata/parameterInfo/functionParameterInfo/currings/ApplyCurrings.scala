object ApplyCurrings {
  def apply(x: Int)(y: Int) = 2
}

ApplyCurrings(1)(/*caret*/)
//p0: Int