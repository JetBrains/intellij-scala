class ScalaConstructorC[T: Manifest]()

new ScalaConstructorC[Int]()(/*caret*/)
// implicit evidence$1: Manifest[Int]