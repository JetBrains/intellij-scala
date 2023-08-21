class ScalaConstructorC[T: Manifest]()

ScalaConstructorC[Int]()(<caret>)
//TEXT: [T: Manifest]()(using manifest$T$0: Manifest[Int]), STRIKEOUT: false