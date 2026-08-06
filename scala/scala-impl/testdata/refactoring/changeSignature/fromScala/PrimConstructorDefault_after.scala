class Constructor protected(i: Int, b: Boolean = true) {

  def this(i: Int, j: Int) {
    this(i)
  }

  val c = new Constructor(1)
}

class ConstructorChild extends Constructor(0)