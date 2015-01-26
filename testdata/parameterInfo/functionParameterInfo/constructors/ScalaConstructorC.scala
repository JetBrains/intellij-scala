class ScalaConstructorC[T: Manifest]()

new ScalaConstructorC[Int]()(/*caret*/)
// implicit ev1: Manifest[Int]