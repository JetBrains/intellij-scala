class LocalBefore {
  val field1 = 45
  val field2 = 33

  def fiFoo = ???

  def foo2(fiParam: Int) = {
    val fiValue = 67
    fi/*caret*/
  }
}
/*
fiParam
fiValue
field1
field2
fiFoo
finalize
final
*/