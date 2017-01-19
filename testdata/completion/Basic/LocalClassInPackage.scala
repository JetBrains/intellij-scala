package test {

  class Test {
    def foo(): Unit = {
      val r: Seq[Int] = Seq.empty

      case class TestClass(polka: Int, kolka: String)

      val q: TestClass = null
      q.polk/*caret*/
    }
  }

}
/*
polka
 */