case class Container(innerValue: Int)

class OutmostScope {
/*inThisScope*/
  val outerScope = new {
    val innerScope = Container(1)
    /*start*/innerScope.innerValue/*end*/
  }
}

/*
case class Container(innerValue: Int)

class OutmostScope {

  val outerScope = new {
    val innerScope = Container(1)
    testMethodName(innerScope)
  }

  def testMethodName(innerScope: Container): Int = {
    innerScope.innerValue
  }
}
*/