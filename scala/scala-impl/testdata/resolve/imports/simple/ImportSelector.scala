package ddd {
  class N
}

package aaa.bbb {
  object Foo {
    val bar = 42
  }

  object Alex
}


package ccc {
  class Test {
    def m = {
      import aaa.bbb
      import bbb.{Foo => Haha, Alex => Den}

      import Haha._

      <ref>bar
    }
  }
}