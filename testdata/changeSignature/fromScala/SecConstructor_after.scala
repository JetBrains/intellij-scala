class Constructor {

  def this(i: Int, j: Int) {
    this()
  }

  val c = new Constructor(1, 0)
}

class ConstructorChild extends Constructor(0, 0)