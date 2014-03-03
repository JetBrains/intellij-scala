object Sample extends A {
  trait Z {
    def foo {
      "stop here"
    }
  }
  def main(args: Array[String]) {
    new Z {}.foo
  }
}