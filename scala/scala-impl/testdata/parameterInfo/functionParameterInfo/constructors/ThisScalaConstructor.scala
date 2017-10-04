class ThisScalaConstructor {
  def this(x: Int) {
    this()
  }
  def this(x: Boolean) {
    this()
  }
}

new ThisScalaConstructor(<caret>)
/*
<no parameters>
x: Boolean
x: Int
*/