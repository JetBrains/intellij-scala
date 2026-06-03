package object util {
  class Numbers

  object Numbers {
    val PI = 3.14159
  }
}

package testo {

import org.codehaus.groovy.syntax.Numbers

object C {
  import util.Numbers

  class SomeClass {
    /*start*/Numbers.PI/*end*/
  }
}
}
//Double