package ddd {
object Nnn
}

package aaa.bbb {
object Foo {
  val bar = 42
}

object Alex

object Tom
}


package ccc {
class Test {
  def m = {
    import ddd._
    import aaa.bbb
    import bbb.{Foo => Haha, Alex => _, _}

    import Haha._

    <ref>Tom
  }
}
}