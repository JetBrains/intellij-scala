class ThisStmt {
  class B(x: ThisStmt)

  new B(/*start*/this/*end*/) {
    def foo(x: Int) = 45
  }
}
//ThisStmt