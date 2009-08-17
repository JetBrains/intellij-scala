object ObjectApply {
  def apply(s: String) = s + "biaka"
  def foo(s: String) = ObjectApply(s)
}

object Main {
  def main(args: Array[String]) {
    print(<ref>ObjectApply.foo("ti "))
  }
}