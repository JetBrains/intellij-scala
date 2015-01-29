class CaseClauseInput {
  def foo {
    1 match {
      case x => {
        /*start*/
        x + 1
        x + 2
        /*end*/
      }
      case _ =>
    }
  }
}
/*
class CaseClauseInput {
  def foo {
    1 match {
      case x => {
        /*start*/
        testMethodName(x)
        /*end*/
      }
      case _ =>
    }
  }

  def testMethodName(x: Int): Unit = {
    x + 1
    x + 2
  }
}
*/