package org.jetbrains.plugins.scala.lang.findUsages

import org.jetbrains.plugins.scala.extensions.StringExt

class ScalaUsageTypeProviderTest_Scala2 extends ScalaUsageTypeProviderTestBase {

  def test_assignment(): Unit = doTest(
    """
      |class Test {
      |  var x = 0
      |  x = 42
      |  def method(): Unit = {
      |    x = 99
      |  }
      |}
      |""".stripMargin.withNormalizedSeparator,
    """scala.FILE
      |  ScClass[Test]
      |    extends block
      |      template body -> Value read
      |        variable definition -> Value read
      |          pattern list -> Value read
      |            reference pattern[x] -> Value read
      |          IntegerLiteral -> Value read
      |        assign statement -> Value read
      |          Reference expression[x] -> Value write
      |          IntegerLiteral -> Value read
      |        function definition[method] -> Value read
      |          parameter clauses -> Value read
      |            parameter clause -> Value read
      |          simple type -> Method return type
      |            reference[Unit] -> Method return type
      |          block of expressions -> Value read
      |            { -> Value read
      |            assign statement -> Value read
      |              Reference expression[x] -> Value write
      |              IntegerLiteral -> Value read
      |            } -> Value read
      |""".stripMargin.withNormalizedSeparator
  )

  def testNewInstanceCreationAndInheritance(): Unit = {
    doTest(
      """trait MyTrait1
        |trait MyTrait2
        |class MyClass extends MyTrait1 with MyTrait2
        |
        |//new instance creation
        |new MyClass with MyTrait1 with MyTrait2
        |new MyClass
        |
        |//new anonimous class instance creation
        |new MyClass with MyTrait1 with MyTrait2 {}
        |new MyClass {}
        |
        |//inheritance
        |class MyChildClass1 extends MyClass with MyTrait1 with MyTrait2
        |class MyChildClass2 extends MyClass
        |""".stripMargin,
      """scala.FILE
        |  ScTrait[MyTrait1]
        |  ScTrait[MyTrait2]
        |  ScClass[MyClass]
        |    extends block
        |      template parents
        |        constructor -> Usage in extends/implements clause
        |          simple type -> Usage in extends/implements clause
        |            reference[MyTrait1] -> Usage in extends/implements clause
        |        constructor -> Usage in extends/implements clause
        |          simple type -> Usage in extends/implements clause
        |            reference[MyTrait2] -> Usage in extends/implements clause
        |  comment
        |  ScNewTemplateDefinition[<anonymous>]
        |    extends block
        |      template parents
        |        constructor -> New instance creation
        |          simple type -> New instance creation
        |            reference[MyClass] -> New instance creation
        |        constructor -> Usage in extends/implements clause
        |          simple type -> Usage in extends/implements clause
        |            reference[MyTrait1] -> Usage in extends/implements clause
        |        constructor -> Usage in extends/implements clause
        |          simple type -> Usage in extends/implements clause
        |            reference[MyTrait2] -> Usage in extends/implements clause
        |  ScNewTemplateDefinition[<anonymous>]
        |    extends block
        |      template parents
        |        constructor -> New instance creation
        |          simple type -> New instance creation
        |            reference[MyClass] -> New instance creation
        |  comment
        |  ScNewTemplateDefinition[<anonymous>]
        |    extends block
        |      template parents
        |        constructor -> New anonymous inheritor creation
        |          simple type -> New anonymous inheritor creation
        |            reference[MyClass] -> New anonymous inheritor creation
        |        constructor -> Usage in extends/implements clause
        |          simple type -> Usage in extends/implements clause
        |            reference[MyTrait1] -> Usage in extends/implements clause
        |        constructor -> Usage in extends/implements clause
        |          simple type -> Usage in extends/implements clause
        |            reference[MyTrait2] -> Usage in extends/implements clause
        |      template body -> Value read
        |  ScNewTemplateDefinition[<anonymous>]
        |    extends block
        |      template parents
        |        constructor -> New anonymous inheritor creation
        |          simple type -> New anonymous inheritor creation
        |            reference[MyClass] -> New anonymous inheritor creation
        |      template body -> Value read
        |  ScClass[MyChildClass1]
        |    extends block
        |      template parents
        |        constructor -> Usage in extends/implements clause
        |          simple type -> Usage in extends/implements clause
        |            reference[MyClass] -> Usage in extends/implements clause
        |        constructor -> Usage in extends/implements clause
        |          simple type -> Usage in extends/implements clause
        |            reference[MyTrait1] -> Usage in extends/implements clause
        |        constructor -> Usage in extends/implements clause
        |          simple type -> Usage in extends/implements clause
        |            reference[MyTrait2] -> Usage in extends/implements clause
        |  ScClass[MyChildClass2]
        |    extends block
        |      template parents
        |        constructor -> Usage in extends/implements clause
        |          simple type -> Usage in extends/implements clause
        |            reference[MyClass] -> Usage in extends/implements clause
        |""".stripMargin
    )
  }


  //NOTE: this test covers current behaviour
  //It might not reflect the 100% desired behaviour
  //(e.g. maybe in the future we decide that find usage on class should also show apply methods invocations)
  def testCaseClass(): Unit = doTest(
    """case class MyCaseClass(x: Int)
      |
      |new MyCaseClass(42)
      |MyCaseClass(42)
      |""".stripMargin,
    """scala.FILE
      |  ScClass[MyCaseClass]
      |    modifiers
      |    primary constructor
      |      parameter clauses
      |        parameter clause
      |          class parameter[x] -> Method parameter declaration
      |            parameter type -> Method parameter declaration
      |              simple type -> Method parameter declaration
      |                reference[Int] -> Method parameter declaration
      |  ScNewTemplateDefinition[<anonymous>]
      |    extends block
      |      template parents
      |        constructor -> New instance creation
      |          simple type -> New instance creation
      |            reference[MyCaseClass] -> New instance creation
      |          arguments of function -> Value read
      |            IntegerLiteral -> Value read
      |  Method call
      |    Reference expression[MyCaseClass] -> Method `apply`
      |    arguments of function -> Value read
      |      IntegerLiteral -> Value read
      |""".stripMargin
  )

  //SCL-9119
  def testSCL9119(): Unit = doTest(
    """object ApplyBug {
      |  class Foo {
      |    def apply(t: Int): Int = 2
      |  }
      |
      |  def foo = new Foo
      |
      |  def a(i: Int): Int = new Foo()(i)
      |  def b(i: Int): Int = foo(i)
      |}
      |""".stripMargin,
    """scala.FILE
      |  ScObject[ApplyBug]
      |    extends block
      |      template body -> Value read
      |        ScClass[Foo] -> Value read
      |          extends block -> Value read
      |            template body -> Value read
      |              function definition[apply] -> Value read
      |                parameter clauses -> Value read
      |                  parameter clause -> Value read
      |                    parameter[t] -> Method parameter declaration
      |                      parameter type -> Method parameter declaration
      |                        simple type -> Method parameter declaration
      |                          reference[Int] -> Method parameter declaration
      |                simple type -> Method return type
      |                  reference[Int] -> Method return type
      |                IntegerLiteral -> Value read
      |        function definition[foo] -> Value read
      |          ScNewTemplateDefinition[<anonymous>] -> Value read
      |            extends block -> Value read
      |              template parents -> Value read
      |                constructor -> New instance creation
      |                  simple type -> New instance creation
      |                    reference[Foo] -> New instance creation
      |        function definition[a] -> Value read
      |          parameter clauses -> Value read
      |            parameter clause -> Value read
      |              parameter[i] -> Method parameter declaration
      |                parameter type -> Method parameter declaration
      |                  simple type -> Method parameter declaration
      |                    reference[Int] -> Method parameter declaration
      |          simple type -> Method return type
      |            reference[Int] -> Method return type
      |          ScNewTemplateDefinition[<anonymous>] -> Value read
      |            extends block -> Value read
      |              template parents -> Value read
      |                constructor -> New instance creation
      |                  simple type -> New instance creation
      |                    reference[Foo] -> New instance creation
      |                  arguments of function -> Value read
      |                  arguments of function -> Value read
      |                    Reference expression[i] -> Value read
      |        function definition[b] -> Value read
      |          parameter clauses -> Value read
      |            parameter clause -> Value read
      |              parameter[i] -> Method parameter declaration
      |                parameter type -> Method parameter declaration
      |                  simple type -> Method parameter declaration
      |                    reference[Int] -> Method parameter declaration
      |          simple type -> Method return type
      |            reference[Int] -> Method return type
      |          Method call -> Value read
      |            Reference expression[foo] -> Method `apply`
      |            arguments of function -> Value read
      |              Reference expression[i] -> Value read
      |""".stripMargin
  )

  //SCL-8887
  def testSCL8887(): Unit = doTest(
    """class Test(var name: String) {
      |  println(name)
      |  name = "blah"
      |}
      |""".stripMargin,
    """scala.FILE
      |  ScClass[Test]
      |    primary constructor
      |      parameter clauses
      |        parameter clause
      |          class parameter[name] -> Method parameter declaration
      |            parameter type -> Method parameter declaration
      |              simple type -> Method parameter declaration
      |                reference[String] -> Method parameter declaration
      |    extends block
      |      template body -> Value read
      |        Method call -> Value read
      |          Reference expression[println] -> Value read
      |          arguments of function -> Value read
      |            Reference expression[name] -> Value read
      |        assign statement -> Value read
      |          Reference expression[name] -> Value write
      |          StringLiteral -> Value read
      |""".stripMargin
  )

  def testSCL17348_1(): Unit = doTest(
    """def foo: String = ???
      |println(foo)
      |new String(foo)
      |""".stripMargin,
    """scala.FILE
      |  function definition[foo]
      |    simple type -> Method return type
      |      reference[String] -> Method return type
      |    Reference expression[???]
      |  Method call
      |    Reference expression[println]
      |    arguments of function -> Value read
      |      Reference expression[foo] -> Value read
      |  ScNewTemplateDefinition[<anonymous>]
      |    extends block
      |      template parents
      |        constructor -> New instance creation
      |          simple type -> New instance creation
      |            reference[String] -> New instance creation
      |          arguments of function -> Value read
      |            Reference expression[foo] -> Value read
      |""".stripMargin
  )

  def testSCL17348_2(): Unit = doTest(
    """class ScalaClass[T](x: T) {
      |  new JavaClass(42)
      |  JavaClass.MY_CONSTANT
      |
      |  new ScalaClass(JavaClass.MY_CONSTANT)
      |  new ScalaClass[Int](JavaClass.MY_CONSTANT)
      |}
      |""".stripMargin,
    """scala.FILE
      |  ScClass[ScalaClass]
      |    type parameter clause
      |      type parameter[T]
      |    primary constructor
      |      parameter clauses
      |        parameter clause
      |          class parameter[x] -> Method parameter declaration
      |            parameter type -> Method parameter declaration
      |              simple type -> Method parameter declaration
      |                reference[T] -> Method parameter declaration
      |    extends block
      |      template body -> Value read
      |        ScNewTemplateDefinition[<anonymous>] -> Value read
      |          extends block -> Value read
      |            template parents -> Value read
      |              constructor -> New instance creation
      |                simple type -> New instance creation
      |                  reference[JavaClass] -> New instance creation
      |                arguments of function -> Value read
      |                  IntegerLiteral -> Value read
      |        Reference expression[MY_CONSTANT] -> Value read
      |          Reference expression[JavaClass] -> Value read
      |        ScNewTemplateDefinition[<anonymous>] -> Value read
      |          extends block -> Value read
      |            template parents -> Value read
      |              constructor -> New instance creation
      |                simple type -> New instance creation
      |                  reference[ScalaClass] -> New instance creation
      |                arguments of function -> Value read
      |                  Reference expression[MY_CONSTANT] -> Value read
      |                    Reference expression[JavaClass] -> Value read
      |        ScNewTemplateDefinition[<anonymous>] -> Value read
      |          extends block -> Value read
      |            template parents -> Value read
      |              constructor -> New instance creation
      |                type generic call -> New instance creation
      |                  simple type -> New instance creation
      |                    reference[ScalaClass] -> New instance creation
      |                  type arguments -> Type parameter
      |                    simple type -> Type parameter
      |                      reference[Int] -> Type parameter
      |                arguments of function -> Value read
      |                  Reference expression[MY_CONSTANT] -> Value read
      |                    Reference expression[JavaClass] -> Value read
      |""".stripMargin
  )

  def testClassWithMultipleConstructors_FromSecondaryConstructorInvocation(): Unit = doTest(
    s"""class MyClass(s: String) {
       |  def this(x: Int) = this(x.toString)
       |  def this(x: Short) = this(x.toInt)
       |}
       |""".stripMargin,
    """scala.FILE
      |  ScClass[MyClass]
      |    primary constructor
      |      parameter clauses
      |        parameter clause
      |          class parameter[s] -> Method parameter declaration
      |            parameter type -> Method parameter declaration
      |              simple type -> Method parameter declaration
      |                reference[String] -> Method parameter declaration
      |    extends block
      |      template body -> Value read
      |        function definition[this] -> Value read
      |          parameter clauses -> Value read
      |            parameter clause -> Value read
      |              parameter[x] -> Method parameter declaration
      |                parameter type -> Method parameter declaration
      |                  simple type -> Method parameter declaration
      |                    reference[Int] -> Method parameter declaration
      |          self invocation -> Secondary constructor
      |            arguments of function -> Value read
      |              Reference expression[toString] -> Value read
      |                Reference expression[x] -> Value read
      |        function definition[this] -> Value read
      |          parameter clauses -> Value read
      |            parameter clause -> Value read
      |              parameter[x] -> Method parameter declaration
      |                parameter type -> Method parameter declaration
      |                  simple type -> Method parameter declaration
      |                    reference[Short] -> Method parameter declaration
      |          self invocation -> Secondary constructor
      |            arguments of function -> Value read
      |              Reference expression[toInt] -> Value read
      |                Reference expression[x] -> Value read
      |""".stripMargin
  )
}
