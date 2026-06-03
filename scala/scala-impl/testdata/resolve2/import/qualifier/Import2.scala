object foo {
  object bar {
    class baz
  }
}

import /* resolved: false */ bar.baz
import foo._

println(classOf[/* resolved: false */ baz])
