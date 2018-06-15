class ScalaConstructorD[T: Manifest]() {
  def this(a: Int) = this()
}

new ScalaConstructorD[Int](0)(<caret>)
// ()(implicit ev$1: Manifest[Int])