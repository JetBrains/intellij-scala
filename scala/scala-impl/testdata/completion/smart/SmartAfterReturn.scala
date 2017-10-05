class SmartAfterReturn {
  class B
  def foo: B = {
    val gigabyte = new B
    return gi/*caret*/
  }
}
//gigabyte