case class Container(innerValue: Int)

object outermost {
  class Outer(innerScope: Container) {
/*inThisScope*/
    val outerScope = new {
      /*start*/innerScope.innerValue/*end*/
    }
  }
}
/*
case class Container(innerValue: Int)

object outermost {
  class Outer(innerScope: Container) {

    val outerScope = new {
      testMethodName
    }

    def testMethodName: Int = {
      innerScope.innerValue
    }
  }
}
*/