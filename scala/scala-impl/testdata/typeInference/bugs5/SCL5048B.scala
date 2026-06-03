object SCL5048B {

  trait Foo {
    type Bar
    def bar: Bar
  }

  object AFoo extends Foo {
    type Bar = String
    def bar = ""
  }

  def bar(foo: Foo): foo.Bar = /*start*/foo.bar/*end*/ /* Expression of type DepMethTypes.Foo#Bar doesn't conform to expected type foo.type#Bar */

  val s: String = bar(AFoo) /* Expression of type foo.type#Bar doesn't conform to expected type String */

}
//foo.Bar