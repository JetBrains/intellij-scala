package a {
  package b {
    class BB
  }
  import b.BB

  class C extends /*caret*/BB
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