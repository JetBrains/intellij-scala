package foo.baz

import foo.bar.NameAfterRename

class Repro {
  def op(r: NameAfterRename): Unit = r match {
    case NameAfterRename.ChildA => ()
    case NameAfterRename.ChildB => ()
  }
}
