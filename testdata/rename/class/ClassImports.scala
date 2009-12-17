package a {
  package b {
    class B
  }
  import b.B

  class C extends /*caret*/B
}
/*
package a {
  package b {
    class NameAfterRename
  }
  import b.NameAfterRename

  class C extends /*caret*/NameAfterRename
}
 */
