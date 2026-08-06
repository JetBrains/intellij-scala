case class Container(innerValue: Int)

class OutmostScope {
  val outerScope = new {
/*inThisScope*/
    val innerScope = Container(1)
    /*start*/innerScope.innerValue/*end*/
  }
}

/*
case class Container(innerValue: Int)

class OutmostScope {
  val outerScope = new {

    val innerScope = Container(1)
    testMethodName

    def testMethodName: Int = {
      innerScope.innerValue
    }
  }
}
*/