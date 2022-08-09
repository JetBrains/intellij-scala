class CaseClauseParamAsLocal {

  abstract case class Base(i: Int)

  case class A(b: Int) extends Base(b)

  class CaseClauseAsLocal(classParam: Base) {
    def testCase = {
      classParam match {
        case A(retparam) =>
          val retLocal = 23
          ret<caret>
        case _ =>
      }
    }

    val retField = 45
  }

}
