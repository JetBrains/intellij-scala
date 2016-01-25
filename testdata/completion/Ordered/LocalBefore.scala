class LocalBefore {
  val field1 = 45

  def fiFoo = ???

  val fil1, fil2: Int

  def foo2(fiParam: Int) = {
    val fiValue = 67
    fi/*caret*/
  }
}
/*
fiParam
fiValue
field1
fil1
fil2
fiFoo
finalize
final
*/