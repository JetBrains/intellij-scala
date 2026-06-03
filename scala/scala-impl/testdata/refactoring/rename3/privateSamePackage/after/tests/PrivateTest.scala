package tests

class PrivateTest {
  val p = new NameAfterRename
  p.foo
  NameAfterRename.bar
}