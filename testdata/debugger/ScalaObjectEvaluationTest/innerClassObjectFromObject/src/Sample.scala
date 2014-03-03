object Sample {
  class S {
    object SS {
      object S {
        def foo() {
          SS.S //to have $outer field
          "stop here"
        }
      }
      object G
    }
    def foo() {
      SS.S.foo()
    }
  }

  def main(args: Array[String]) {
    val x = new S()
    x.foo()
  }
}