object MyConv {
  implicit def StringTo(s: String): {def capitalize: Int} = new {
    def capitalize = 0
  }
}

// See http://lampsvn.epfl.ch/trac/scala/ticket/1931

import MyConv.StringTo
import Predef.{stringWrapper => _}

"".<ref>capitalize

