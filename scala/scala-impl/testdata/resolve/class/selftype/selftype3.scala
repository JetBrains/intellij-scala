package aaa {

trait MyClass1 {
}

trait MyTrait {
  trait Inner
}

class Foo extends MyClass1 with MyTrait{
  self =>
  val in: self.<ref>Inner
}

}