object Test {
  class A {
    val foo: String => String = null
    def foo(x: Int) = 1
  }

  (new A)./* line: 3 */foo("")
  (new A)./* line: 4 */foo(4)
}