object Test {

  class Test1 extends ParameterlessOverriders2 {
    override def bar(b: Boolean): Int = 1

    val x = bar(true)
    this.bar(true)
  }

  object Test2 extends ParameterlessOverriders2 {
    override def bar(b: Boolean): Int = 1

    val x = bar(true)
    this.bar(true)
  }

  class Test3 extends ParameterlessOverriders2 {
    override def bar(b: Boolean): Int = 1

    val x = bar(true)
  }

  trait Test4 extends ParameterlessOverriders2 {
    override def bar(b: Boolean): Int = 1

    val x = bar(true)
  }

}