package moveRefactoring.foo

import moveRefactoring.bar.B

case class D(x: Int) {
  val b = B(x)
}