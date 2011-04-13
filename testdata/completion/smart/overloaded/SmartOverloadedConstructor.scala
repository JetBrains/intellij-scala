class SmartOverloadedConstructor {
  val nononInt = 1
  val nononBoolean = false
  class A(x: Int) {
    def this(x: Boolean) {
      this(2)
    }
  }
  new A(nonon/*caret*/)
}
/*
nononBoolean
nononInt
*/