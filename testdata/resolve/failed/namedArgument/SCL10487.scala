class Test {
  def apply(arg1: Int, arg2: Boolean) = println(arg2)
}

object Foo {
  def main(args: Array[String]): Unit = {
    new Test()(15, <ref>arg2 = false) // arg2 is red, but compiles
  }
}