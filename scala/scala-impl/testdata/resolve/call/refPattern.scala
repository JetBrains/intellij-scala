class C {
  def foo = 42
}

abstract class A {

  val MY_C: C

  def bar = 42 match {
    case test @ (MY_C) => test.fo<ref>o
  }
}
