object TypeInference {
  class A {
    var x: Double = 0
  }
  def fill(as: List[A]) {
    as.foreach(_.x = /*start*/1/*end*/)
  }
}
//Double