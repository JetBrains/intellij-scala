class Constructor {

  def <caret>this(i: Int) {
    this()
  }

  val c = new Constructor(1)
}

class ConstructorChild extends Constructor(0)