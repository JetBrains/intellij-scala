class UnapplyInCaseClause {

  object Example {
    def unapply(arg: Int): Option[Int] = if (arg % 2 == 0) Some(arg + 1) else None
  }

  def foo(t: Test): Unit = {
    t match {
      case Example(<caret>
    }
  }
}
