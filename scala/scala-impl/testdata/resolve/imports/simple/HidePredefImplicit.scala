object MyConv {
  implicit def StringTo(s: String): {def caPitalize: Int} = new {
    def caPitalize = 0
  }
}

// See http://lampsvn.epfl.ch/trac/scala/ticket/1931

import MyConv.StringTo

"".<ref>caPitalize

