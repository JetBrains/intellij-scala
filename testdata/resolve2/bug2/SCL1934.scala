package p {

object Container {
  private[p] val invisible = "smth"
}
package p2 {

import p.Container

object User {
  Container./*resolved: true*/invisible
}

}
}

package p3 {

import p.Container

object User {
  Container./*accessible: false*/invisible
}

}

