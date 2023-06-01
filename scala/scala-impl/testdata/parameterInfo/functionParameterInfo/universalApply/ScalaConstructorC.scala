class ScalaConstructorC[T: Manifest]()

ScalaConstructorC[Int]()(<caret>)
// [T: Manifest]()(implicit manifest$T$0: Manifest[Int])