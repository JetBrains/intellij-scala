class ScalaConstructorB(implicit a: Int)

new ScalaConstructorB()(<caret>)
//TEXT: ()(implicit a: Int), STRIKEOUT: false