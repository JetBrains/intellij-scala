class Simple extends A {
  trait Z {
    def foo {
      "stop here"
    }
  }
  def main(args: Array[String]) {
    new Z {}.foo
  }
}
object Sample {
  def main(args: Array[String]) {
    val sample = new Simple
    sample.main(args)
  }
}