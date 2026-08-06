package aaa {

trait MyClass1 {
}

trait MyTrait {
  trait Inner
}

class Foo extends MyTrait {
  self =>
  val in: <ref>Inner
}

}