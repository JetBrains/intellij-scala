class AbstractExpectedType {
  val a: Seq[Int] = null
  def s : Seq[Int] = {
    /*start*/a.flatMap(i =>
      Seq.empty
    )/*end*/
  }
}
//Seq[Nothing]