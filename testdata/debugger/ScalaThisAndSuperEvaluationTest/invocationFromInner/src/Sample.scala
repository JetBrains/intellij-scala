object Sample extends A {
  trait Z {
    def goo {
      "stop here"
    }
  }
  def main(args: Array[String]) {
    new Z {}.goo
  }
}