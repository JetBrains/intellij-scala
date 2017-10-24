package target

import source.A

object UtilT {
  def bar() = A.a()
  def baz() = (new A).a()
}
