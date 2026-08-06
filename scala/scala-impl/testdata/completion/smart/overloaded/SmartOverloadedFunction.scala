class SmartOverloadedFunction {
  val nononInt = 1
  val nononBoolean = false
  def foo(x: Int) = 1
  def foo(x: Boolean) = false
  foo(nonon/*caret*/)
}
/*
nononBoolean
nononInt
*/