object Example {
  implicit class PimpedLong(x: Long) {
    def show() = println(s"Long value is $x")
  }
  1.<ref>show()
}