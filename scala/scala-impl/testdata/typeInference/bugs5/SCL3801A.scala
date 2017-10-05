object Test {
  def main(args: Array[String]) {
    /*start*/CaseClass.curried/*end*/
  }
}

// the companion object extends AbstractFunction2 which has for example the 'curried' method
case class CaseClass(x: Int, y: Int)
//Int => Int => CaseClass