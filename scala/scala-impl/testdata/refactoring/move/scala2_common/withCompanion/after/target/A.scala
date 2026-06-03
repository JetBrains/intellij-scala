// Comment before
package target

import source.UtilS

class A {
  def a() = UtilS.foo()
}

object A {
  def a() = UtilS.foo()
}
