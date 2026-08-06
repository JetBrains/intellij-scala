package p

class Test {}

object O {
  def m() {
    val test: Test = /*start*/new Test()/*end*/
  }
}
/*
package p

class Test {}

object O {
  def m(param: Test) {
    val test: Test = /*start*/param/*end*/
  }
}
*/