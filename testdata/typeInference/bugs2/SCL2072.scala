object test {
  trait Transformation {
    def transform(x: Double): Double
  }

  // Annotating `: Transformation` fixes the problem.
  val zero1 = new Transformation {
    def transform(x: Double) = 0d
  }

  // commenting out the companion object fixes the resolve error!!
  object Transformation

  /*start*/zero1.transform(1d)/*end*/

}
//Double