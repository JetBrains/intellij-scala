object foo {
  object bar {
    class baz
  }
}

import foo._
import /* */ bar.baz

println(classOf[/* path: foo.bar.baz */ baz])