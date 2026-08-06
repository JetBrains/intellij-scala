class FewCalls {

  class X[M] {
    def apply[X] = new {
      def apply[Y]: (M, X, Y) = sys.error("")
    }
  }

  /*start*/new X[Int][String][Boolean]/*end*/
}
//(Int, String, Boolean)