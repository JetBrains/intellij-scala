package tests

class PrivateTest {
  val p = new /*caret*/Private
  p.foo
  /*caret*/Private.bar
}