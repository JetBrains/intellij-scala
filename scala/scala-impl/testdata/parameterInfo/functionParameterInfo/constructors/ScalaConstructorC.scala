class ScalaConstructorC[T: Manifest]()

new ScalaConstructorC[Int]()(<caret>)
// ()(implicit manifest$T: Manifest[Int])