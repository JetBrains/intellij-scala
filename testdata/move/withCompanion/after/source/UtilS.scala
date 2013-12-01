package source

import target.A

object UtilS {
  def foo() {}
  def bar() {
    A.a()
  }
  def baz() {
    (new A).a()
  }
}
