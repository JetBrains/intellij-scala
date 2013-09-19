object X {
  val /*caret*/a = 1

  def foo() = {
    a + a
  }

  val b = a
}
/*
object X {
  def foo() = {
    1 + 1
  }

  val b = 1
}*/