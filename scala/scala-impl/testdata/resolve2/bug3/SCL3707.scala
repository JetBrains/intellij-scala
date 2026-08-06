package SCL3707

object Test {
  import a./*resolved: true*/b./*resolved: true*/a./*resolved: true*/Foo

  def main(args: Array[String]) {
    println("foo")
  }
}

package a {
  package b {
    package a {
      class Foo
    }
  }
  class Bar
}
