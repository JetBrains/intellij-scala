object Test {
  class Test1 extends ParameterlessOverriders2 {
    override def foo = 1

    val x = foo
    this foo
  }
  object Test2 extends ParameterlessOverriders2 {
    override val foo = 1

    val x = foo
    (this foo)
  }
  class Test3 extends ParameterlessOverriders2 {
    override var foo = 1

    val x = foo
  }
  trait Test4 extends ParameterlessOverriders2 {
    override def foo() = 1

    val x = foo()
  }
}