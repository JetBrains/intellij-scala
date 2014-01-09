package target

import target.A

object UtilT {
  def bar() = A.a()
  def baz() = (new A).a()
}
