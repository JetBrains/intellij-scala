object DepMethTypes {

  trait Foo {
    type Bar
    def bar: Bar
  }

  object AFoo extends Foo {
    type Bar = String
    def bar = ""
  }

  val x: Foo = null

  def bar(foo: Foo): foo.Bar = foo.bar /* Expression of type DepMethTypes.Foo#Bar doesn't conform to expected type foo.type#Bar */
  val s: String = /*start*/bar(AFoo)/*end*/ /* Expression of type foo.type#Bar doesn't conform to expected type String */
}
//DepMethTypes.AFoo.Bar