class <caret>Constructor(i: Int) {

  def this(i: Int, j: Int) {
    this(i)
  }

  val c = new Constructor(1)
}

class ConstructorChild extends Constructor(0)