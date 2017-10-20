package a {
package b {
class U
}
package object b {
  def U = 123
}
}

package c {
import a.b.U

object C {
  val x: U = new U
  /*start*/x/*end*/
}
}
//U