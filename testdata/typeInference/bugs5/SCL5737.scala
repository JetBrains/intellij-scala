object SCL5737 {
object Test extends Enumeration {
  type Test = Value
  val Bar, Baz, Qux = Value
}

class Test2 {
  val map: Map[Test.Value, Int] = /*start*/Map(Test.Bar -> 1, Test.Baz -> 2, Test.Qux -> 3)/*end*/
}
}
//Map[SCL5737.Test.Value, Int]