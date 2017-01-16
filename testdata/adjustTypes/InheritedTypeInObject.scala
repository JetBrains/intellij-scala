package foo.bar

class Base {
  class CLS
  type TPE
}

/*start*/object Test extends Base {
  type TPE = String

  val x: foo.bar.Test.CLS = ???
  val y: foo.bar.Test.TPE = ???

}/*end*/
/*
package foo.bar

class Base {
  class CLS
  type TPE
}

object Test extends Base {
  type TPE = String

  val x: Test.CLS = ???
  val y: String = ???

}
*/