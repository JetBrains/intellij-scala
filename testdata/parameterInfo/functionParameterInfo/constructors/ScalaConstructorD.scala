class ScalaConstructorD[T: Manifest]() {
  def this(a: Int) = this()
}

new ScalaConstructorD[Int](0)(/*caret*/)
// implicit ev1: Manifest[Int]