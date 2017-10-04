package scala {
  package object H {
    def foo(): Int = 1
  }
}

package zlo {
  object Z {
    /*start*/H.foo()/*end*/
  }
}
//Int