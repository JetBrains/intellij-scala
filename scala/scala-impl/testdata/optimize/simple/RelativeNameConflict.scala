// Notification message: Removed 1 import, added 1 import
package a {

  import test.A
  import test.test
  import A.AA

  class Usages {
    val aa = AA
    val t = test
  }
}

package test {

  object A {
    object AA
  }

  object test
}
/*
package a {

  import test.{A, test}
  import _root_.test.A.AA

  class Usages {
    val aa = AA
    val t = test
  }
}

package test {

  object A {
    object AA
  }

  object test
}
*/