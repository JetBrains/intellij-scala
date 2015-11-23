package tin

class FixImport extends G {
  val x = new /*ref*/E
}
/*
package tin

import bin._
import _root_.tin.bon.G

class FixImport extends G {
  val x = new E
}
*/