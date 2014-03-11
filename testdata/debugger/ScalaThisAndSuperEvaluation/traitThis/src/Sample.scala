object Sample {
  trait Z {
    def foo {
      "stop here"
    }
  }
  def main(args: Array[String]) {
    new Z {}.foo
  }
}