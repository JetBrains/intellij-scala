package tests

class PrivateTest {
  val p = new test.Public
  p./*caret*/foo
}