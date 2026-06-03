package a {
  package b {
    class BBB

    object BBB
  }
  import b.BBB

  class C extends /*caret*/BBB
}
/*
package a {
  package b {
    class NameAfterRename

    object NameAfterRename
  }
  import b.NameAfterRename

  class C extends /*caret*/NameAfterRename
}
*/