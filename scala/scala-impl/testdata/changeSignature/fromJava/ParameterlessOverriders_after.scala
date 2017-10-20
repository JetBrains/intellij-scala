object Test {

  class Test1 extends ParameterlessOverriders {
    override def bar: Int = 1

    val x = bar
    this bar
  }

  object Test2 extends ParameterlessOverriders {
    override val bar: Int = 1
    val x = bar
    (this bar)
  }

  class Test3 extends ParameterlessOverriders {
    override var bar: Int = 1
    val x = bar
  }

  trait Test4 extends ParameterlessOverriders {
    override def bar(): Int = 1

    val x = bar()
  }

}