package tests

package NameAfterRename:
  val foo = 42
end NameAfterRename

def test(): Unit =
  println(NameAfterRename.foo)
