object SCL3458 {
  def handle[R >: Null <: AnyRef](body : => R) : (R, Boolean) = {
    try {
      val r = body
      (r, true)
    } catch {
      case e : Exception =>
        (null, false)
    }
  }

  def handle(body : => Unit) : Boolean = {
    try {
      body
      true
    } catch {
      case e : Exception =>
        false
    }
  }

  def main(args : Array[String]) {
    def foo(i : Int) {
      if (i % 2 == 0)
        throw new Exception("foo")
    }

    <ref>handle {  // here
      foo(2)
    }
  }

}