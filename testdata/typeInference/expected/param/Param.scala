class Foo[T] {
  def act(f : T => String) {}
}

object Main {
  def main = {
    val foo = new Foo[String]
    foo.act(x => <ref>x.toString)
  }
}

