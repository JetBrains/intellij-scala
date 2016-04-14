package `hello` {

  class AAA
}

package `world` { <ref>`hello`.AAA

  object X {
    val aaa = new `hello`.AAA()
  }
}