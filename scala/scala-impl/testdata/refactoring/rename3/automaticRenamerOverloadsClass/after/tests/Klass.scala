package tests

class Klass {
  def bar(): Unit = {
    "".bar()
  }

  def bar(a: Int): Unit = {
  }

  extension (s: String) def bar(): Unit = {
  }

}

class Sub extends Klass {
  override def bar(a: Int): Unit = {
  }
}

@main def main(): Unit = {
  new Klass().bar()
  Klass().bar(1)

  // extension
  locally {
    val klass = new Klass()
    klass.bar("")()
    import klass.bar
    "".bar()
  }
}
