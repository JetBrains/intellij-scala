package tin

import bin.A
import bin.B
import bin.C
import bin.D
import bon.G

class FixImport extends G {
  val x = new /*ref*/E
}
/*
import _root_.bin._
import _root_.tin.bon.G
*/