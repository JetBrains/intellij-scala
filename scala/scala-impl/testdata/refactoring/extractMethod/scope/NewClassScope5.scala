case class Container(innerValue: Int)

object outermost {
  class Outer(innerScope: Container) {
    val outerScope = new {
      /*start*/innerScope.innerValue/*end*/
    }
  }
/*inThisScope*/
}
/*
case class Container(innerValue: Int)

object outermost {
  class Outer(innerScope: Container) {
    val outerScope = new {
      testMethodName(innerScope)
    }
  }

  def testMethodName(innerScope: Container): Int = {
    innerScope.innerValue
  }
}
*/