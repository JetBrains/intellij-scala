class ScalaConstructorC[T: Manifest]()

new ScalaConstructorC[Int]()(<caret>)
// ()(implicit ev$1: Manifest[Int])