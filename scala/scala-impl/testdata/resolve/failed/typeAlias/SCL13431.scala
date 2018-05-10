class Test[+A, +B](a: A, b: B)

object Test {
  type T[A] = Test[A, Int]

  new <ref>T[Int](1, 2)
}