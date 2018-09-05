class ScalaConstructorC[T: Manifest]()

new ScalaConstructorC[Int]()(<caret>)
// ()(implicit manifest$Int: Manifest[Int])