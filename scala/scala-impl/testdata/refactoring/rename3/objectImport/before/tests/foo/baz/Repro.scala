package foo.baz

import foo.bar.RenameMe

class Repro {
  def op(r: /*caret*/RenameMe): Unit = r match {
    case /*caret*/RenameMe.ChildA => ()
    case RenameMe.ChildB => ()
  }
}
