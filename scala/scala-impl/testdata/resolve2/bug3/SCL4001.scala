package a {

class X

class Y(x: X) {
  def y = 8
}

object Y {
  private[a] implicit def x2y(x: X) = new Y(x)
}

}

package b {

import a.X
import a.Y._

object tst {
  new X()./* resolved: false */y
}

}