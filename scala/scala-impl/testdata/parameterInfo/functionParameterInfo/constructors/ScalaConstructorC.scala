class ScalaConstructorC[T: Manifest]()

new ScalaConstructorC[Int]()(<caret>)
// ()(implicit manifest$T$0: Manifest[Int])