class ScalaConstructorC[T: Manifest]()

new ScalaConstructorC[Int]()(<caret>)
// [T: Manifest]()(implicit manifest$T$0: Manifest[Int])