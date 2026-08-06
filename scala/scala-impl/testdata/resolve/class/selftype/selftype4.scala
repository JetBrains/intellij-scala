package aaa {

trait MyClass1 {
}

trait MyTrait {
  trait Inner
}

class Foo extends MyClass1 {
  self : MyTrait =>
  val in: self.<ref>Inner
}

}