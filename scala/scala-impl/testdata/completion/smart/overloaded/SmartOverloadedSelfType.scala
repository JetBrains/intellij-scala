class SmartOverloadedSelfType {
  val nononInt = 1
  val nononBoolean = false
  class A(x: Int) {
    def this(x: Boolean) {
      this(2)
    }
    def this(z: String) {
      this(nonon/*caret*/)
    }
  }
}
/*
nononBoolean
nononInt
*/