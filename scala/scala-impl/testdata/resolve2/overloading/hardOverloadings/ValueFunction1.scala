object Test {
  class A {
    val foo: String => String = null
    def foo(x: Int) = 1
  }

  (new A)./* file: Function1, name: apply */foo("")
  (new A)./* line: 4 */foo(4)
}