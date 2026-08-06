implicit class MyInterp(private val ctx: StringContext) extends AnyVal {
  def myitrp(args: Any*): String = "..."
}
val /*caret*/bbb = "bbb"
val str = myitrp"blah 1 blah $bbb"
/*
implicit class MyInterp(private val ctx: StringContext) extends AnyVal {
  def myitrp(args: Any*): String = "..."
}
val str = myitrp"blah 1 blah bbb"
*/