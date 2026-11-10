class ScalaConstructorC[T: Manifest]()

ScalaConstructorC[Int]()(<caret>)
//TEXT: [T: Manifest]()(implicit manifest$T$0: Manifest[Int]), STRIKEOUT: false