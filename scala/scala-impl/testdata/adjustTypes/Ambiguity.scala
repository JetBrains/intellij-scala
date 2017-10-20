package test {

object A {

  object A {}

  class A {}

}

}

object B {
  val a: /*start*/test.A.A/*end*/ = null
}

/*
import test.A
package test {

object A {

  object A {}

  class A {}

}

}

object B {
  val a: A.A = null
}
*/