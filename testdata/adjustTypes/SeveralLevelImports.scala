package testing.a {

object AA {
  trait AAA {}
}

}

import testing.a
import a.AA
import AA.{AAA => A}

class B {
  val aaa: /*start*/testing.a.AA.AAA/*end*/ = null
}

/*
package testing.a {

object AA {
  trait AAA {}
}

}

import testing.a
import a.AA
import AA.{AAA => A}

class B {
  val aaa: /*start*/A/*end*/ = null
}
*/