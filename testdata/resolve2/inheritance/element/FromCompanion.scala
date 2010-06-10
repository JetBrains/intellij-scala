object test {
  trait A {
    def foo: Int
  }
  object A extends A
  object B extends A

  (new A{})./* */foo
  B./* */foo
  A./* */foo
}