implicit class MyInterp(private val ctx: StringContext) extends AnyVal {
  def myitrp(args: Any*): String = "..."
}
val /*caret*/aaa = 1
val bbb = "bbb"
val str = myitrp"blah $aaa blah $bbb"
/*
implicit class MyInterp(private val ctx: StringContext) extends AnyVal {
  def myitrp(args: Any*): String = "..."
}
val bbb = "bbb"
val str = myitrp"blah 1 blah $bbb"
*/