class ThisScalaConstructor {
  def this(x: Int) {
    this()
  }
  def this(x: Boolean) {
    this()
  }
}

ThisScalaConstructor(<caret>)
/*
<no parameters>
x: Boolean
x: Int
*/