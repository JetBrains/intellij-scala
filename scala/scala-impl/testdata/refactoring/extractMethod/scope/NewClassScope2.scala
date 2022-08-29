case class Container(innerValue: Int)

class OutmostScope {
  val member = Container(1)
  val outerScope = new {
    /*start*/member.innerValue/*end*/
  }
}
/*
case class Container(innerValue: Int)

class OutmostScope {
  val member = Container(1)
  val outerScope = new {
    testMethodName
  }

  def testMethodName: Int = {
    member.innerValue
  }
}
*/