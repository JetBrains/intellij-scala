package testing.a {
trait AAA {}
}

package testing.b {
import testing.a.{AAA => BBB}

class B {
  val aaa: /*start*/testing.a.AAA/*end*/ = null
}
}

/*
package testing.a {
trait AAA {}
}

package testing.b {
import testing.a.{AAA => BBB}

class B {
  val aaa: BBB = null
}
}

*/