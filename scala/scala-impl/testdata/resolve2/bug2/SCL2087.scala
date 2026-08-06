object Test
{
  def foo(bar: () => String) {
    println(bar())
  }

  def foo(x: Int, y: Int) = 1

  def main(args: Array[String]) {
    /* line: 3 */foo(bar) // <- error here
  }

  def bar() = {
    "string"
  }
}