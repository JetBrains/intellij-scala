package ddd {
  class N
}

package aaa.bbb {
  object Foo {
    val bar = 42
  }
}


package ccc {
  class Test {
    def m = {
      import aaa._
      import bbb._
      import Foo._

      <ref>bar
    }
  }
}