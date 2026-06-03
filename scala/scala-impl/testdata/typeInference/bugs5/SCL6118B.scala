class `Test & Test` {
 def foo = 123
}

object Gg extends `Test & Test`


object K {
  /*start*/Gg.foo/*end*/
}
//Int