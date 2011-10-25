object IPTest {
  def foo(dummy: String)(implicit str: String) {
    def returnBool(number: Int)(implicit str: String) = {
      number == 5
    }

    /*start*/List(1, 2, 3, 4, 5).toStream.takeWhile(returnBool)/*end*/
  }
}
//Stream[Int]