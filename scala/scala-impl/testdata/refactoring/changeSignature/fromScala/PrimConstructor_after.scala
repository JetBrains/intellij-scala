class Constructor protected(i: Int, b: Boolean) {

  def this(i: Int, j: Int) {
    this(i, true)
  }

  val c = new Constructor(1, true)
}

class ConstructorChild extends Constructor(0, true)