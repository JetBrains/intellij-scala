package tests

package NameAfterRename:
  val somethingElse = 42
end NameAfterRename

def test1(): Unit =
  println(NameAfterRename.foo)
