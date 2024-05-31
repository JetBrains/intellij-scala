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
      |      template body
      |        variable definition -> Value read
      |          pattern list -> Value read
      |            reference pattern[x] -> Pattern
      |          IntegerLiteral
      |        assign statement -> Value read
      |          Reference expression[x] -> Value write
      |          IntegerLiteral
      |        function definition[method] -> Value read
      |          parameter clauses
      |            parameter clause
      |          simple type -> Method return type
      |            reference[Unit] -> Method return type
      |          block of expressions
      |            {
      |            assign statement -> Value read
      |              Reference expression[x] -> Value write
      |              IntegerLiteral
      |            }
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
        |//new anonymous class instance creation
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
        |      template body
        |  ScNewTemplateDefinition[<anonymous>]
        |    extends block
        |      template parents
        |        constructor -> New anonymous inheritor creation
        |          simple type -> New anonymous inheritor creation
        |            reference[MyClass] -> New anonymous inheritor creation
        |      template body
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
      |          arguments of function
      |            IntegerLiteral
      |  Method call
      |    Reference expression[MyCaseClass] -> Method `apply`
      |    arguments of function
      |      IntegerLiteral
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
      |      template body
      |        ScClass[Foo] -> Value read
      |          extends block -> Value read
      |            template body
      |              function definition[apply] -> Value read
      |                parameter clauses
      |                  parameter clause
      |                    parameter[t] -> Method parameter declaration
      |                      parameter type -> Method parameter declaration
      |                        simple type -> Method parameter declaration
      |                          reference[Int] -> Method parameter declaration
      |                simple type -> Method return type
      |                  reference[Int] -> Method return type
      |                IntegerLiteral
      |        function definition[foo] -> Value read
      |          ScNewTemplateDefinition[<anonymous>] -> Value read
      |            extends block -> Value read
      |              template parents -> Value read
      |                constructor -> New instance creation
      |                  simple type -> New instance creation
      |                    reference[Foo] -> New instance creation
      |        function definition[a] -> Value read
      |          parameter clauses
      |            parameter clause
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
      |                  arguments of function
      |                  arguments of function
      |                    Reference expression[i] -> Value read
      |        function definition[b] -> Value read
      |          parameter clauses
      |            parameter clause
      |              parameter[i] -> Method parameter declaration
      |                parameter type -> Method parameter declaration
      |                  simple type -> Method parameter declaration
      |                    reference[Int] -> Method parameter declaration
      |          simple type -> Method return type
      |            reference[Int] -> Method return type
      |          Method call -> Value read
      |            Reference expression[foo] -> Method `apply`
      |            arguments of function
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
      |      template body
      |        Method call -> Value read
      |          Reference expression[println] -> Value read
      |          arguments of function
      |            Reference expression[name] -> Value read
      |        assign statement -> Value read
      |          Reference expression[name] -> Value write
      |          StringLiteral
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
      |    arguments of function
      |      Reference expression[foo] -> Value read
      |  ScNewTemplateDefinition[<anonymous>]
      |    extends block
      |      template parents
      |        constructor -> New instance creation
      |          simple type -> New instance creation
      |            reference[String] -> New instance creation
      |          arguments of function
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
      |      template body
      |        ScNewTemplateDefinition[<anonymous>] -> Value read
      |          extends block -> Value read
      |            template parents -> Value read
      |              constructor -> New instance creation
      |                simple type -> New instance creation
      |                  reference[JavaClass] -> New instance creation
      |                arguments of function
      |                  IntegerLiteral
      |        Reference expression[MY_CONSTANT] -> Value read
      |          Reference expression[JavaClass] -> Value read
      |        ScNewTemplateDefinition[<anonymous>] -> Value read
      |          extends block -> Value read
      |            template parents -> Value read
      |              constructor -> New instance creation
      |                simple type -> New instance creation
      |                  reference[ScalaClass] -> New instance creation
      |                arguments of function
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
      |                arguments of function
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
      |      template body
      |        function definition[this] -> Value read
      |          parameter clauses
      |            parameter clause
      |              parameter[x] -> Method parameter declaration
      |                parameter type -> Method parameter declaration
      |                  simple type -> Method parameter declaration
      |                    reference[Int] -> Method parameter declaration
      |          self invocation -> Secondary constructor
      |            arguments of function
      |              Reference expression[toString] -> Value read
      |                Reference expression[x] -> Value read
      |        function definition[this] -> Value read
      |          parameter clauses
      |            parameter clause
      |              parameter[x] -> Method parameter declaration
      |                parameter type -> Method parameter declaration
      |                  simple type -> Method parameter declaration
      |                    reference[Short] -> Method parameter declaration
      |          self invocation -> Secondary constructor
      |            arguments of function
      |              Reference expression[toInt] -> Value read
      |                Reference expression[x] -> Value read
      |""".stripMargin
  )

  private val MyBaseClassWithFunctionDefinitions =
    """class MyBaseClass[T <: AnyRef] {
      |  def foo0: String = ???
      |  def foo1(x: Int): String = ???
      |  def foo2(t: T): String = ???
      |  def foo3[E](e: E): String = ???
      |  def foo4[E](e: E, t: T): String = ???
      |}
      |""".stripMargin

  def testDelegateToSuperMethod_SameParameters(): Unit = {
    myFixture.addFileToProject("definitions.scala", MyBaseClassWithFunctionDefinitions)
    doTest(
      s"""class MyChildClass_DelegateToSuper_SameParameters extends MyBaseClass[String] {
         |  override def foo0 = super.foo0
         |  override def foo1(x: Int) = super.foo1(x)
         |  override def foo2(t: String) = super[MyBaseClass].foo2(t)
         |  override def foo3[E](e: E) = super.foo3(e)
         |  override def foo4[E](e: E, t: String) = super.foo4(e, t)
         |}
         |""".stripMargin,
      """scala.FILE
        |  ScClass[MyChildClass_DelegateToSuper_SameParameters]
        |    extends block
        |      template parents
        |        constructor -> Usage in extends/implements clause
        |          type generic call -> Usage in extends/implements clause
        |            simple type -> Usage in extends/implements clause
        |              reference[MyBaseClass] -> Usage in extends/implements clause
        |            type arguments -> Type parameter
        |              simple type -> Type parameter
        |                reference[String] -> Type parameter
        |      template body
        |        function definition[foo0] -> Value read
        |          modifiers -> Value read
        |          Reference expression[foo0] -> Delegate to super method
        |            Super reference -> Delegate to super method
        |        function definition[foo1] -> Value read
        |          modifiers -> Value read
        |          parameter clauses
        |            parameter clause
        |              parameter[x] -> Method parameter declaration
        |                parameter type -> Method parameter declaration
        |                  simple type -> Method parameter declaration
        |                    reference[Int] -> Method parameter declaration
        |          Method call -> Value read
        |            Reference expression[foo1] -> Delegate to super method
        |              Super reference -> Delegate to super method
        |            arguments of function
        |              Reference expression[x] -> Value read
        |        function definition[foo2] -> Value read
        |          modifiers -> Value read
        |          parameter clauses
        |            parameter clause
        |              parameter[t] -> Method parameter declaration
        |                parameter type -> Method parameter declaration
        |                  simple type -> Method parameter declaration
        |                    reference[String] -> Method parameter declaration
        |          Method call -> Value read
        |            Reference expression[foo2] -> Delegate to super method
        |              Super reference -> Delegate to super method
        |            arguments of function
        |              Reference expression[t] -> Value read
        |        function definition[foo3] -> Value read
        |          modifiers -> Value read
        |          type parameter clause
        |            type parameter[E] -> Value read
        |          parameter clauses
        |            parameter clause
        |              parameter[e] -> Method parameter declaration
        |                parameter type -> Method parameter declaration
        |                  simple type -> Method parameter declaration
        |                    reference[E] -> Method parameter declaration
        |          Method call -> Value read
        |            Reference expression[foo3] -> Delegate to super method
        |              Super reference -> Delegate to super method
        |            arguments of function
        |              Reference expression[e] -> Value read
        |        function definition[foo4] -> Value read
        |          modifiers -> Value read
        |          type parameter clause
        |            type parameter[E] -> Value read
        |          parameter clauses
        |            parameter clause
        |              parameter[e] -> Method parameter declaration
        |                parameter type -> Method parameter declaration
        |                  simple type -> Method parameter declaration
        |                    reference[E] -> Method parameter declaration
        |              parameter[t] -> Method parameter declaration
        |                parameter type -> Method parameter declaration
        |                  simple type -> Method parameter declaration
        |                    reference[String] -> Method parameter declaration
        |          Method call -> Value read
        |            Reference expression[foo4] -> Delegate to super method
        |              Super reference -> Delegate to super method
        |            arguments of function
        |              Reference expression[e] -> Value read
        |              Reference expression[t] -> Value read
        |""".stripMargin
    )
  }

  def testDelegateToSuperMethod_DifferentParameters(): Unit = {
    myFixture.addFileToProject("definitions.scala", MyBaseClassWithFunctionDefinitions)
    doTest(
      s"""|class MyChildClass_DelegateToSuper_DifferentParameters extends MyBaseClass[String] {
          |  override def foo1(x: Int) = super.foo1(42)
          |  override def foo2(t: String) = super.foo2("42")
          |  override def foo3[E](e: E) = super.foo3(???)
          |  override def foo4[E](e: E, t: String) = super.foo4(???, ???)
          |}""".stripMargin,
      """scala.FILE
        |  ScClass[MyChildClass_DelegateToSuper_DifferentParameters]
        |    extends block
        |      template parents
        |        constructor -> Usage in extends/implements clause
        |          type generic call -> Usage in extends/implements clause
        |            simple type -> Usage in extends/implements clause
        |              reference[MyBaseClass] -> Usage in extends/implements clause
        |            type arguments -> Type parameter
        |              simple type -> Type parameter
        |                reference[String] -> Type parameter
        |      template body
        |        function definition[foo1] -> Value read
        |          modifiers -> Value read
        |          parameter clauses
        |            parameter clause
        |              parameter[x] -> Method parameter declaration
        |                parameter type -> Method parameter declaration
        |                  simple type -> Method parameter declaration
        |                    reference[Int] -> Method parameter declaration
        |          Method call -> Value read
        |            Reference expression[foo1] -> Delegate to super method
        |              Super reference -> Delegate to super method
        |            arguments of function
        |              IntegerLiteral
        |        function definition[foo2] -> Value read
        |          modifiers -> Value read
        |          parameter clauses
        |            parameter clause
        |              parameter[t] -> Method parameter declaration
        |                parameter type -> Method parameter declaration
        |                  simple type -> Method parameter declaration
        |                    reference[String] -> Method parameter declaration
        |          Method call -> Value read
        |            Reference expression[foo2] -> Delegate to super method
        |              Super reference -> Delegate to super method
        |            arguments of function
        |              StringLiteral
        |        function definition[foo3] -> Value read
        |          modifiers -> Value read
        |          type parameter clause
        |            type parameter[E] -> Value read
        |          parameter clauses
        |            parameter clause
        |              parameter[e] -> Method parameter declaration
        |                parameter type -> Method parameter declaration
        |                  simple type -> Method parameter declaration
        |                    reference[E] -> Method parameter declaration
        |          Method call -> Value read
        |            Reference expression[foo3] -> Delegate to super method
        |              Super reference -> Delegate to super method
        |            arguments of function
        |              Reference expression[???] -> Value read
        |        function definition[foo4] -> Value read
        |          modifiers -> Value read
        |          type parameter clause
        |            type parameter[E] -> Value read
        |          parameter clauses
        |            parameter clause
        |              parameter[e] -> Method parameter declaration
        |                parameter type -> Method parameter declaration
        |                  simple type -> Method parameter declaration
        |                    reference[E] -> Method parameter declaration
        |              parameter[t] -> Method parameter declaration
        |                parameter type -> Method parameter declaration
        |                  simple type -> Method parameter declaration
        |                    reference[String] -> Method parameter declaration
        |          Method call -> Value read
        |            Reference expression[foo4] -> Delegate to super method
        |              Super reference -> Delegate to super method
        |            arguments of function
        |              Reference expression[???] -> Value read
        |              Reference expression[???] -> Value read
        |""".stripMargin
    )
  }

  def testDelegateToSuperMethod_DifferentMethod(): Unit = {
    myFixture.addFileToProject("definitions.scala", MyBaseClassWithFunctionDefinitions)
    doTest(
      s"""class MyChildClass_DelegateToSuper_DifferentMethod extends MyBaseClass[String] {
         |  override def foo0 = super.foo1(42)
         |  override def foo1(x: Int) = super.foo0
         |  override def foo2(t: String) = super.foo0
         |  override def foo3[E](e: E) = super.foo0
         |  override def foo4[E](e: E, t: String) = super.foo0
         |}""".stripMargin,
      """scala.FILE
        |  ScClass[MyChildClass_DelegateToSuper_DifferentMethod]
        |    extends block
        |      template parents
        |        constructor -> Usage in extends/implements clause
        |          type generic call -> Usage in extends/implements clause
        |            simple type -> Usage in extends/implements clause
        |              reference[MyBaseClass] -> Usage in extends/implements clause
        |            type arguments -> Type parameter
        |              simple type -> Type parameter
        |                reference[String] -> Type parameter
        |      template body
        |        function definition[foo0] -> Value read
        |          modifiers -> Value read
        |          Method call -> Value read
        |            Reference expression[foo1] -> Value read
        |              Super reference -> Value read
        |            arguments of function
        |              IntegerLiteral
        |        function definition[foo1] -> Value read
        |          modifiers -> Value read
        |          parameter clauses
        |            parameter clause
        |              parameter[x] -> Method parameter declaration
        |                parameter type -> Method parameter declaration
        |                  simple type -> Method parameter declaration
        |                    reference[Int] -> Method parameter declaration
        |          Reference expression[foo0] -> Value read
        |            Super reference -> Value read
        |        function definition[foo2] -> Value read
        |          modifiers -> Value read
        |          parameter clauses
        |            parameter clause
        |              parameter[t] -> Method parameter declaration
        |                parameter type -> Method parameter declaration
        |                  simple type -> Method parameter declaration
        |                    reference[String] -> Method parameter declaration
        |          Reference expression[foo0] -> Value read
        |            Super reference -> Value read
        |        function definition[foo3] -> Value read
        |          modifiers -> Value read
        |          type parameter clause
        |            type parameter[E] -> Value read
        |          parameter clauses
        |            parameter clause
        |              parameter[e] -> Method parameter declaration
        |                parameter type -> Method parameter declaration
        |                  simple type -> Method parameter declaration
        |                    reference[E] -> Method parameter declaration
        |          Reference expression[foo0] -> Value read
        |            Super reference -> Value read
        |        function definition[foo4] -> Value read
        |          modifiers -> Value read
        |          type parameter clause
        |            type parameter[E] -> Value read
        |          parameter clauses
        |            parameter clause
        |              parameter[e] -> Method parameter declaration
        |                parameter type -> Method parameter declaration
        |                  simple type -> Method parameter declaration
        |                    reference[E] -> Method parameter declaration
        |              parameter[t] -> Method parameter declaration
        |                parameter type -> Method parameter declaration
        |                  simple type -> Method parameter declaration
        |                    reference[String] -> Method parameter declaration
        |          Reference expression[foo0] -> Value read
        |            Super reference -> Value read
        |""".stripMargin
    )
  }

  def testCallViaSuperReference_FromAnotherMethod(): Unit = {
    myFixture.addFileToProject("definitions.scala", MyBaseClassWithFunctionDefinitions)
    doTest(
      s"""class MyChildClass_CallViaSuperReference_FromAnotherMethod extends MyBaseClass[String] {
         |  def bar0 = super.foo1(42)
         |  def bar1(x: Int) = super.foo0
         |  def bar2(t: String) = super.foo0
         |  def bar3[E](e: E) = super.foo0
         |  def bar4[E](e: E, t: String) = super.foo0
         |}""".stripMargin,
      """scala.FILE
        |  ScClass[MyChildClass_CallViaSuperReference_FromAnotherMethod]
        |    extends block
        |      template parents
        |        constructor -> Usage in extends/implements clause
        |          type generic call -> Usage in extends/implements clause
        |            simple type -> Usage in extends/implements clause
        |              reference[MyBaseClass] -> Usage in extends/implements clause
        |            type arguments -> Type parameter
        |              simple type -> Type parameter
        |                reference[String] -> Type parameter
        |      template body
        |        function definition[bar0] -> Value read
        |          Method call -> Value read
        |            Reference expression[foo1] -> Value read
        |              Super reference -> Value read
        |            arguments of function
        |              IntegerLiteral
        |        function definition[bar1] -> Value read
        |          parameter clauses
        |            parameter clause
        |              parameter[x] -> Method parameter declaration
        |                parameter type -> Method parameter declaration
        |                  simple type -> Method parameter declaration
        |                    reference[Int] -> Method parameter declaration
        |          Reference expression[foo0] -> Value read
        |            Super reference -> Value read
        |        function definition[bar2] -> Value read
        |          parameter clauses
        |            parameter clause
        |              parameter[t] -> Method parameter declaration
        |                parameter type -> Method parameter declaration
        |                  simple type -> Method parameter declaration
        |                    reference[String] -> Method parameter declaration
        |          Reference expression[foo0] -> Value read
        |            Super reference -> Value read
        |        function definition[bar3] -> Value read
        |          type parameter clause
        |            type parameter[E] -> Value read
        |          parameter clauses
        |            parameter clause
        |              parameter[e] -> Method parameter declaration
        |                parameter type -> Method parameter declaration
        |                  simple type -> Method parameter declaration
        |                    reference[E] -> Method parameter declaration
        |          Reference expression[foo0] -> Value read
        |            Super reference -> Value read
        |        function definition[bar4] -> Value read
        |          type parameter clause
        |            type parameter[E] -> Value read
        |          parameter clauses
        |            parameter clause
        |              parameter[e] -> Method parameter declaration
        |                parameter type -> Method parameter declaration
        |                  simple type -> Method parameter declaration
        |                    reference[E] -> Method parameter declaration
        |              parameter[t] -> Method parameter declaration
        |                parameter type -> Method parameter declaration
        |                  simple type -> Method parameter declaration
        |                    reference[String] -> Method parameter declaration
        |          Reference expression[foo0] -> Value read
        |            Super reference -> Value read
        |""".stripMargin
    )
  }

  //NOTE: The correct behaviour for deleting to other instances has not yet been implemented
  //The expected data reflects current behaviour and not the desired one
  def testDelegateToOtherInstance_SameParameters(): Unit = {
    myFixture.addFileToProject("definitions.scala", MyBaseClassWithFunctionDefinitions)
    doTest(
      s"""class MyChildClass_DelegateToOtherInstance_SameParameters extends MyBaseClass[String] {
         |  private val delegate: MyBaseClass[String] = ???
         |  override def foo0 = delegate.foo0
         |  override def foo1(x: Int) = delegate.foo1(x)
         |  override def foo2(t: String) = delegate.foo2(t)
         |  override def foo3[E](e: E) = delegate.foo3(e)
         |  override def foo4[E](e: E, t: String) = delegate.foo4(e, t)
         |}""".stripMargin,
      """scala.FILE
        |  ScClass[MyChildClass_DelegateToOtherInstance_SameParameters]
        |    extends block
        |      template parents
        |        constructor -> Usage in extends/implements clause
        |          type generic call -> Usage in extends/implements clause
        |            simple type -> Usage in extends/implements clause
        |              reference[MyBaseClass] -> Usage in extends/implements clause
        |            type arguments -> Type parameter
        |              simple type -> Type parameter
        |                reference[String] -> Type parameter
        |      template body
        |        value definition -> Value read
        |          modifiers -> Value read
        |            access modifier -> Access Modifier
        |          pattern list -> Value read
        |            reference pattern[delegate] -> Pattern
        |          type generic call -> Field declaration
        |            simple type -> Field declaration
        |              reference[MyBaseClass] -> Field declaration
        |            type arguments -> Type parameter
        |              simple type -> Type parameter
        |                reference[String] -> Type parameter
        |          Reference expression[???] -> Value read
        |        function definition[foo0] -> Value read
        |          modifiers -> Value read
        |          Reference expression[foo0] -> Value read
        |            Reference expression[delegate] -> Value read
        |        function definition[foo1] -> Value read
        |          modifiers -> Value read
        |          parameter clauses
        |            parameter clause
        |              parameter[x] -> Method parameter declaration
        |                parameter type -> Method parameter declaration
        |                  simple type -> Method parameter declaration
        |                    reference[Int] -> Method parameter declaration
        |          Method call -> Value read
        |            Reference expression[foo1] -> Value read
        |              Reference expression[delegate] -> Value read
        |            arguments of function
        |              Reference expression[x] -> Value read
        |        function definition[foo2] -> Value read
        |          modifiers -> Value read
        |          parameter clauses
        |            parameter clause
        |              parameter[t] -> Method parameter declaration
        |                parameter type -> Method parameter declaration
        |                  simple type -> Method parameter declaration
        |                    reference[String] -> Method parameter declaration
        |          Method call -> Value read
        |            Reference expression[foo2] -> Value read
        |              Reference expression[delegate] -> Value read
        |            arguments of function
        |              Reference expression[t] -> Value read
        |        function definition[foo3] -> Value read
        |          modifiers -> Value read
        |          type parameter clause
        |            type parameter[E] -> Value read
        |          parameter clauses
        |            parameter clause
        |              parameter[e] -> Method parameter declaration
        |                parameter type -> Method parameter declaration
        |                  simple type -> Method parameter declaration
        |                    reference[E] -> Method parameter declaration
        |          Method call -> Value read
        |            Reference expression[foo3] -> Value read
        |              Reference expression[delegate] -> Value read
        |            arguments of function
        |              Reference expression[e] -> Value read
        |        function definition[foo4] -> Value read
        |          modifiers -> Value read
        |          type parameter clause
        |            type parameter[E] -> Value read
        |          parameter clauses
        |            parameter clause
        |              parameter[e] -> Method parameter declaration
        |                parameter type -> Method parameter declaration
        |                  simple type -> Method parameter declaration
        |                    reference[E] -> Method parameter declaration
        |              parameter[t] -> Method parameter declaration
        |                parameter type -> Method parameter declaration
        |                  simple type -> Method parameter declaration
        |                    reference[String] -> Method parameter declaration
        |          Method call -> Value read
        |            Reference expression[foo4] -> Value read
        |              Reference expression[delegate] -> Value read
        |            arguments of function
        |              Reference expression[e] -> Value read
        |              Reference expression[t] -> Value read
        |""".stripMargin
    )
  }

  //NOTE: The correct behaviour for deleting to other instances has not yet been implemented
  //The expected data reflects current behaviour and not the desired one
  def testDelegateToOtherInstance_DifferentParameters(): Unit = {
    myFixture.addFileToProject("definitions.scala", MyBaseClassWithFunctionDefinitions)
    doTest(
      s"""class MyChildClass_DelegateToOtherInstance_DifferentParameters extends MyBaseClass[String] {
         |  private val delegate: MyBaseClass[String] = ???
         |  override def foo1(x: Int) = delegate.foo1(42)
         |  override def foo2(t: String) = delegate.foo2("42")
         |  override def foo3[E](e: E) = delegate.foo3(???)
         |  override def foo4[E](e: E, t: String) = delegate.foo4(???, ???)
         |}""".stripMargin,
      """scala.FILE
        |  ScClass[MyChildClass_DelegateToOtherInstance_DifferentParameters]
        |    extends block
        |      template parents
        |        constructor -> Usage in extends/implements clause
        |          type generic call -> Usage in extends/implements clause
        |            simple type -> Usage in extends/implements clause
        |              reference[MyBaseClass] -> Usage in extends/implements clause
        |            type arguments -> Type parameter
        |              simple type -> Type parameter
        |                reference[String] -> Type parameter
        |      template body
        |        value definition -> Value read
        |          modifiers -> Value read
        |            access modifier -> Access Modifier
        |          pattern list -> Value read
        |            reference pattern[delegate] -> Pattern
        |          type generic call -> Field declaration
        |            simple type -> Field declaration
        |              reference[MyBaseClass] -> Field declaration
        |            type arguments -> Type parameter
        |              simple type -> Type parameter
        |                reference[String] -> Type parameter
        |          Reference expression[???] -> Value read
        |        function definition[foo1] -> Value read
        |          modifiers -> Value read
        |          parameter clauses
        |            parameter clause
        |              parameter[x] -> Method parameter declaration
        |                parameter type -> Method parameter declaration
        |                  simple type -> Method parameter declaration
        |                    reference[Int] -> Method parameter declaration
        |          Method call -> Value read
        |            Reference expression[foo1] -> Value read
        |              Reference expression[delegate] -> Value read
        |            arguments of function
        |              IntegerLiteral
        |        function definition[foo2] -> Value read
        |          modifiers -> Value read
        |          parameter clauses
        |            parameter clause
        |              parameter[t] -> Method parameter declaration
        |                parameter type -> Method parameter declaration
        |                  simple type -> Method parameter declaration
        |                    reference[String] -> Method parameter declaration
        |          Method call -> Value read
        |            Reference expression[foo2] -> Value read
        |              Reference expression[delegate] -> Value read
        |            arguments of function
        |              StringLiteral
        |        function definition[foo3] -> Value read
        |          modifiers -> Value read
        |          type parameter clause
        |            type parameter[E] -> Value read
        |          parameter clauses
        |            parameter clause
        |              parameter[e] -> Method parameter declaration
        |                parameter type -> Method parameter declaration
        |                  simple type -> Method parameter declaration
        |                    reference[E] -> Method parameter declaration
        |          Method call -> Value read
        |            Reference expression[foo3] -> Value read
        |              Reference expression[delegate] -> Value read
        |            arguments of function
        |              Reference expression[???] -> Value read
        |        function definition[foo4] -> Value read
        |          modifiers -> Value read
        |          type parameter clause
        |            type parameter[E] -> Value read
        |          parameter clauses
        |            parameter clause
        |              parameter[e] -> Method parameter declaration
        |                parameter type -> Method parameter declaration
        |                  simple type -> Method parameter declaration
        |                    reference[E] -> Method parameter declaration
        |              parameter[t] -> Method parameter declaration
        |                parameter type -> Method parameter declaration
        |                  simple type -> Method parameter declaration
        |                    reference[String] -> Method parameter declaration
        |          Method call -> Value read
        |            Reference expression[foo4] -> Value read
        |              Reference expression[delegate] -> Value read
        |            arguments of function
        |              Reference expression[???] -> Value read
        |              Reference expression[???] -> Value read
        |""".stripMargin
    )
  }

  //NOTE: The correct behaviour for deleting to other instances has not yet been implemented
  //The expected data reflects current behaviour and not the desired one
  def testDelegateToOtherInstance_DifferentMethod(): Unit = {
    myFixture.addFileToProject("definitions.scala", MyBaseClassWithFunctionDefinitions)
    doTest(
      s"""class MyChildClass_DelegateToOtherInstance_DifferentMethod extends MyBaseClass[String] {
         |  private val delegate: MyBaseClass[String] = ???
         |  override def foo0 = delegate.foo1(42)
         |  override def foo1(x: Int) = delegate.foo0
         |  override def foo2(t: String) = delegate.foo0
         |  override def foo3[E](e: E) = delegate.foo0
         |  override def foo4[E](e: E, t: String) = delegate.foo0
         |}
         |""".stripMargin,
      """scala.FILE
        |  ScClass[MyChildClass_DelegateToOtherInstance_DifferentMethod]
        |    extends block
        |      template parents
        |        constructor -> Usage in extends/implements clause
        |          type generic call -> Usage in extends/implements clause
        |            simple type -> Usage in extends/implements clause
        |              reference[MyBaseClass] -> Usage in extends/implements clause
        |            type arguments -> Type parameter
        |              simple type -> Type parameter
        |                reference[String] -> Type parameter
        |      template body
        |        value definition -> Value read
        |          modifiers -> Value read
        |            access modifier -> Access Modifier
        |          pattern list -> Value read
        |            reference pattern[delegate] -> Pattern
        |          type generic call -> Field declaration
        |            simple type -> Field declaration
        |              reference[MyBaseClass] -> Field declaration
        |            type arguments -> Type parameter
        |              simple type -> Type parameter
        |                reference[String] -> Type parameter
        |          Reference expression[???] -> Value read
        |        function definition[foo0] -> Value read
        |          modifiers -> Value read
        |          Method call -> Value read
        |            Reference expression[foo1] -> Value read
        |              Reference expression[delegate] -> Value read
        |            arguments of function
        |              IntegerLiteral
        |        function definition[foo1] -> Value read
        |          modifiers -> Value read
        |          parameter clauses
        |            parameter clause
        |              parameter[x] -> Method parameter declaration
        |                parameter type -> Method parameter declaration
        |                  simple type -> Method parameter declaration
        |                    reference[Int] -> Method parameter declaration
        |          Reference expression[foo0] -> Value read
        |            Reference expression[delegate] -> Value read
        |        function definition[foo2] -> Value read
        |          modifiers -> Value read
        |          parameter clauses
        |            parameter clause
        |              parameter[t] -> Method parameter declaration
        |                parameter type -> Method parameter declaration
        |                  simple type -> Method parameter declaration
        |                    reference[String] -> Method parameter declaration
        |          Reference expression[foo0] -> Value read
        |            Reference expression[delegate] -> Value read
        |        function definition[foo3] -> Value read
        |          modifiers -> Value read
        |          type parameter clause
        |            type parameter[E] -> Value read
        |          parameter clauses
        |            parameter clause
        |              parameter[e] -> Method parameter declaration
        |                parameter type -> Method parameter declaration
        |                  simple type -> Method parameter declaration
        |                    reference[E] -> Method parameter declaration
        |          Reference expression[foo0] -> Value read
        |            Reference expression[delegate] -> Value read
        |        function definition[foo4] -> Value read
        |          modifiers -> Value read
        |          type parameter clause
        |            type parameter[E] -> Value read
        |          parameter clauses
        |            parameter clause
        |              parameter[e] -> Method parameter declaration
        |                parameter type -> Method parameter declaration
        |                  simple type -> Method parameter declaration
        |                    reference[E] -> Method parameter declaration
        |              parameter[t] -> Method parameter declaration
        |                parameter type -> Method parameter declaration
        |                  simple type -> Method parameter declaration
        |                    reference[String] -> Method parameter declaration
        |          Reference expression[foo0] -> Value read
        |            Reference expression[delegate] -> Value read
        |""".stripMargin
    )
  }

  private val MyBaseClassWithFunctionDefinitionsForValOverriders =
    """class MyBaseClass {
      |  def foo1: Int = 1
      |  def foo2: Int = 2
      |}
      |""".stripMargin

  def testDelegateFromValToSuper(): Unit = {
    myFixture.addFileToProject("definitions.scala", MyBaseClassWithFunctionDefinitionsForValOverriders)
    doTest(
      s"""class MyChildClass_DelegateToSuper extends MyBaseClass {
         |  override val foo1: Int = super.foo1
         |  override lazy val foo2: Int = super.foo2
         |}
         |""".stripMargin,
      """scala.FILE
        |  ScClass[MyChildClass_DelegateToSuper]
        |    extends block
        |      template parents
        |        constructor -> Usage in extends/implements clause
        |          simple type -> Usage in extends/implements clause
        |            reference[MyBaseClass] -> Usage in extends/implements clause
        |      template body
        |        value definition -> Value read
        |          modifiers -> Value read
        |          pattern list -> Value read
        |            reference pattern[foo1] -> Pattern
        |          simple type -> Field declaration
        |            reference[Int] -> Field declaration
        |          Reference expression[foo1] -> Delegate to super method
        |            Super reference -> Delegate to super method
        |        value definition -> Value read
        |          modifiers -> Value read
        |          pattern list -> Value read
        |            reference pattern[foo2] -> Pattern
        |          simple type -> Field declaration
        |            reference[Int] -> Field declaration
        |          Reference expression[foo2] -> Delegate to super method
        |            Super reference -> Delegate to super method
        |""".stripMargin
    )
  }

  def testDelegateFromValToSuper_DifferentMethod(): Unit = {
    myFixture.addFileToProject("definitions.scala", MyBaseClassWithFunctionDefinitionsForValOverriders)
    doTest(
      s"""class MyChildClass_DelegateToSuper_DifferentMethod extends MyBaseClass {
         |  override val foo1: Int = super.foo2
         |  override lazy val foo2: Int = super.foo1
         |}""".stripMargin,
      """scala.FILE
        |  ScClass[MyChildClass_DelegateToSuper_DifferentMethod]
        |    extends block
        |      template parents
        |        constructor -> Usage in extends/implements clause
        |          simple type -> Usage in extends/implements clause
        |            reference[MyBaseClass] -> Usage in extends/implements clause
        |      template body
        |        value definition -> Value read
        |          modifiers -> Value read
        |          pattern list -> Value read
        |            reference pattern[foo1] -> Pattern
        |          simple type -> Field declaration
        |            reference[Int] -> Field declaration
        |          Reference expression[foo2] -> Value read
        |            Super reference -> Value read
        |        value definition -> Value read
        |          modifiers -> Value read
        |          pattern list -> Value read
        |            reference pattern[foo2] -> Pattern
        |          simple type -> Field declaration
        |            reference[Int] -> Field declaration
        |          Reference expression[foo1] -> Value read
        |            Super reference -> Value read
        |""".stripMargin
    )
  }

  def testDelegateFromValToSuper_DifferentMethod_MultipleDefinitionInPattern(): Unit = {
    myFixture.addFileToProject("definitions.scala", MyBaseClassWithFunctionDefinitionsForValOverriders)
    doTest(
      s"""class MyChildClass_DelegateToSuper_DifferentMethod_MultipleValues extends MyBaseClass {
         |  override val (foo1, foo2) = (super.foo1, super.foo2)
         |}""".stripMargin,
      """scala.FILE
        |  ScClass[MyChildClass_DelegateToSuper_DifferentMethod_MultipleValues]
        |    extends block
        |      template parents
        |        constructor -> Usage in extends/implements clause
        |          simple type -> Usage in extends/implements clause
        |            reference[MyBaseClass] -> Usage in extends/implements clause
        |      template body
        |        value definition -> Value read
        |          modifiers -> Value read
        |          pattern list -> Value read
        |            Tuple Pattern -> Pattern
        |              patterns -> Pattern
        |                reference pattern[foo1] -> Pattern
        |                reference pattern[foo2] -> Pattern
        |          Tuple -> Value read
        |            Reference expression[foo1] -> Value read
        |              Super reference -> Value read
        |            Reference expression[foo2] -> Value read
        |              Super reference -> Value read
        |""".stripMargin
    )
  }

  def testDelegateFromValToOtherInstance(): Unit = {
    myFixture.addFileToProject("definitions.scala", MyBaseClassWithFunctionDefinitionsForValOverriders)
    doTest(
      s"""class MyChildClass_DelegateToOtherInstance extends MyBaseClass {
         |  private val delegate: MyBaseClass = ???
         |  override val foo1: Int = delegate.foo1
         |  override lazy val foo2: Int = delegate.foo2
         |}""".stripMargin,
      """scala.FILE
        |  ScClass[MyChildClass_DelegateToOtherInstance]
        |    extends block
        |      template parents
        |        constructor -> Usage in extends/implements clause
        |          simple type -> Usage in extends/implements clause
        |            reference[MyBaseClass] -> Usage in extends/implements clause
        |      template body
        |        value definition -> Value read
        |          modifiers -> Value read
        |            access modifier -> Access Modifier
        |          pattern list -> Value read
        |            reference pattern[delegate] -> Pattern
        |          simple type -> Field declaration
        |            reference[MyBaseClass] -> Field declaration
        |          Reference expression[???] -> Value read
        |        value definition -> Value read
        |          modifiers -> Value read
        |          pattern list -> Value read
        |            reference pattern[foo1] -> Pattern
        |          simple type -> Field declaration
        |            reference[Int] -> Field declaration
        |          Reference expression[foo1] -> Value read
        |            Reference expression[delegate] -> Value read
        |        value definition -> Value read
        |          modifiers -> Value read
        |          pattern list -> Value read
        |            reference pattern[foo2] -> Pattern
        |          simple type -> Field declaration
        |            reference[Int] -> Field declaration
        |          Reference expression[foo2] -> Value read
        |            Reference expression[delegate] -> Value read
        |""".stripMargin
    )
  }

  def testDelegateFromValToOtherInstance_DifferentMethod(): Unit = {
    myFixture.addFileToProject("definitions.scala", MyBaseClassWithFunctionDefinitionsForValOverriders)
    doTest(
      s"""class MyChildClass_DelegateToOtherInstance_DifferentMethod extends MyBaseClass {
         |  private val delegate: MyBaseClass = ???
         |  override val foo1: Int = delegate.foo2
         |  override lazy val foo2: Int = delegate.foo1
         |}""".stripMargin,
      """scala.FILE
        |  ScClass[MyChildClass_DelegateToOtherInstance_DifferentMethod]
        |    extends block
        |      template parents
        |        constructor -> Usage in extends/implements clause
        |          simple type -> Usage in extends/implements clause
        |            reference[MyBaseClass] -> Usage in extends/implements clause
        |      template body
        |        value definition -> Value read
        |          modifiers -> Value read
        |            access modifier -> Access Modifier
        |          pattern list -> Value read
        |            reference pattern[delegate] -> Pattern
        |          simple type -> Field declaration
        |            reference[MyBaseClass] -> Field declaration
        |          Reference expression[???] -> Value read
        |        value definition -> Value read
        |          modifiers -> Value read
        |          pattern list -> Value read
        |            reference pattern[foo1] -> Pattern
        |          simple type -> Field declaration
        |            reference[Int] -> Field declaration
        |          Reference expression[foo2] -> Value read
        |            Reference expression[delegate] -> Value read
        |        value definition -> Value read
        |          modifiers -> Value read
        |          pattern list -> Value read
        |            reference pattern[foo2] -> Pattern
        |          simple type -> Field declaration
        |            reference[Int] -> Field declaration
        |          Reference expression[foo1] -> Value read
        |            Reference expression[delegate] -> Value read
        |""".stripMargin
    )
  }

  def testAnnotation(): Unit = {
    doTest(
      s"""object MyScalaClass {
         |  @Deprecated def foo1(): Unit = ()
         |  @Deprecated() def foo2(): Unit = ()
         |  @Deprecated(forRemoval = true) def foo3(): Unit = ()
         |  @Deprecated("description") def foo4(): Unit = ()
         |  @Deprecated(since = "description") def foo5(): Unit = ()
         |
         |  @java.lang.Deprecated def bar1(): Unit = ()
         |  @java.lang.Deprecated() def bar2(): Unit = ()
         |  @java.lang.Deprecated(forRemoval = true) def bar3(): Unit = ()
         |  @java.lang.Deprecated("description") def bar4(): Unit = ()
         |  @java.lang.Deprecated(since = "description") def bar5(): Unit = ()
         |}""".stripMargin,
      """scala.FILE
        |  ScObject[MyScalaClass]
        |    extends block
        |      template body
        |        function definition[foo1] -> Value read
        |          annotations -> Value read
        |            annotation -> Value read
        |              annotation expression -> Value read
        |                constructor -> Annotation
        |                  simple type -> Annotation
        |                    reference[Deprecated] -> Annotation
        |          parameter clauses
        |            parameter clause
        |          simple type -> Method return type
        |            reference[Unit] -> Method return type
        |          unit expression -> Value read
        |        function definition[foo2] -> Value read
        |          annotations -> Value read
        |            annotation -> Value read
        |              annotation expression -> Value read
        |                constructor -> Annotation
        |                  simple type -> Annotation
        |                    reference[Deprecated] -> Annotation
        |                  arguments of function
        |          parameter clauses
        |            parameter clause
        |          simple type -> Method return type
        |            reference[Unit] -> Method return type
        |          unit expression -> Value read
        |        function definition[foo3] -> Value read
        |          annotations -> Value read
        |            annotation -> Value read
        |              annotation expression -> Value read
        |                constructor -> Annotation
        |                  simple type -> Annotation
        |                    reference[Deprecated] -> Annotation
        |                  arguments of function
        |                    assign statement -> Value read
        |                      Reference expression[forRemoval] -> Value write
        |                      BooleanLiteral
        |          parameter clauses
        |            parameter clause
        |          simple type -> Method return type
        |            reference[Unit] -> Method return type
        |          unit expression -> Value read
        |        function definition[foo4] -> Value read
        |          annotations -> Value read
        |            annotation -> Value read
        |              annotation expression -> Value read
        |                constructor -> Annotation
        |                  simple type -> Annotation
        |                    reference[Deprecated] -> Annotation
        |                  arguments of function
        |                    StringLiteral
        |          parameter clauses
        |            parameter clause
        |          simple type -> Method return type
        |            reference[Unit] -> Method return type
        |          unit expression -> Value read
        |        function definition[foo5] -> Value read
        |          annotations -> Value read
        |            annotation -> Value read
        |              annotation expression -> Value read
        |                constructor -> Annotation
        |                  simple type -> Annotation
        |                    reference[Deprecated] -> Annotation
        |                  arguments of function
        |                    assign statement -> Value read
        |                      Reference expression[since] -> Value write
        |                      StringLiteral
        |          parameter clauses
        |            parameter clause
        |          simple type -> Method return type
        |            reference[Unit] -> Method return type
        |          unit expression -> Value read
        |        function definition[bar1] -> Value read
        |          annotations -> Value read
        |            annotation -> Value read
        |              annotation expression -> Value read
        |                constructor -> Annotation
        |                  simple type -> Annotation
        |                    reference[Deprecated] -> Annotation
        |                      reference[lang] -> Annotation
        |                        reference[java] -> Annotation
        |          parameter clauses
        |            parameter clause
        |          simple type -> Method return type
        |            reference[Unit] -> Method return type
        |          unit expression -> Value read
        |        function definition[bar2] -> Value read
        |          annotations -> Value read
        |            annotation -> Value read
        |              annotation expression -> Value read
        |                constructor -> Annotation
        |                  simple type -> Annotation
        |                    reference[Deprecated] -> Annotation
        |                      reference[lang] -> Annotation
        |                        reference[java] -> Annotation
        |                  arguments of function
        |          parameter clauses
        |            parameter clause
        |          simple type -> Method return type
        |            reference[Unit] -> Method return type
        |          unit expression -> Value read
        |        function definition[bar3] -> Value read
        |          annotations -> Value read
        |            annotation -> Value read
        |              annotation expression -> Value read
        |                constructor -> Annotation
        |                  simple type -> Annotation
        |                    reference[Deprecated] -> Annotation
        |                      reference[lang] -> Annotation
        |                        reference[java] -> Annotation
        |                  arguments of function
        |                    assign statement -> Value read
        |                      Reference expression[forRemoval] -> Value write
        |                      BooleanLiteral
        |          parameter clauses
        |            parameter clause
        |          simple type -> Method return type
        |            reference[Unit] -> Method return type
        |          unit expression -> Value read
        |        function definition[bar4] -> Value read
        |          annotations -> Value read
        |            annotation -> Value read
        |              annotation expression -> Value read
        |                constructor -> Annotation
        |                  simple type -> Annotation
        |                    reference[Deprecated] -> Annotation
        |                      reference[lang] -> Annotation
        |                        reference[java] -> Annotation
        |                  arguments of function
        |                    StringLiteral
        |          parameter clauses
        |            parameter clause
        |          simple type -> Method return type
        |            reference[Unit] -> Method return type
        |          unit expression -> Value read
        |        function definition[bar5] -> Value read
        |          annotations -> Value read
        |            annotation -> Value read
        |              annotation expression -> Value read
        |                constructor -> Annotation
        |                  simple type -> Annotation
        |                    reference[Deprecated] -> Annotation
        |                      reference[lang] -> Annotation
        |                        reference[java] -> Annotation
        |                  arguments of function
        |                    assign statement -> Value read
        |                      Reference expression[since] -> Value write
        |                      StringLiteral
        |          parameter clauses
        |            parameter clause
        |          simple type -> Method return type
        |            reference[Unit] -> Method return type
        |          unit expression -> Value read
        |""".stripMargin
    )
  }

  protected val CommonDeclarationsFoPatternUsages =
    """package org.example
      |
      |class MyClass extends RuntimeException
      |object MyObject extends RuntimeException
      |
      |class MyClassWithExtractor
      |object MyClassWithExtractor {
      |  def unapply(arg: MyClassWithExtractor): Option[String] = ???
      |}""".stripMargin

  protected val MainFileForPatternUsages =
    """import org.example._
      |
      |object Usage {
      |  val value = 42
      |
      |  (??? : Any) match {
      |    case _: MyClass =>
      |    case _: org.example.MyClass => //with FQN
      |    case MyObject =>
      |    case `value` =>
      |    case MyClassWithExtractor(_) =>
      |  }
      |}
      |""".stripMargin

  def testUsageInPatterns(): Unit = {
    myFixture.addFileToProject("definitions.scala", CommonDeclarationsFoPatternUsages)
    doTest(
      MainFileForPatternUsages,
      """scala.FILE
        |  ScImportStatement
        |    import expression -> Usage in import
        |      reference[example] -> Usage in import
        |        reference[org] -> Usage in import
        |  ScObject[Usage]
        |    extends block
        |      template body
        |        value definition -> Value read
        |          pattern list -> Value read
        |            reference pattern[value] -> Pattern
        |          IntegerLiteral
        |        match statement -> Value read
        |          Expression in parentheses -> Value read
        |            typed statement -> Value read
        |              Reference expression[???] -> Value read
        |              simple type -> Typed Statement
        |                reference[Any] -> Typed Statement
        |          case clauses
        |            case clause
        |              typed pattern[_] -> Pattern
        |                Type pattern -> Pattern
        |                  simple type -> Pattern
        |                    reference[MyClass] -> Pattern
        |            case clause
        |              typed pattern[_] -> Pattern
        |                Type pattern -> Pattern
        |                  simple type -> Pattern
        |                    reference[MyClass] -> Pattern
        |                      reference[example] -> Pattern
        |                        reference[org] -> Pattern
        |            case clause
        |              StableElementPattern -> Pattern
        |                Reference expression[MyObject] -> Pattern
        |            case clause
        |              StableElementPattern -> Pattern
        |                Reference expression[`value`] -> Pattern
        |            case clause
        |              Constructor Pattern -> Extractor
        |                reference[MyClassWithExtractor] -> Extractor
        |                Pattern arguments -> Extractor
        |                  any sequence -> Pattern
        |""".stripMargin
    )
  }

  protected val MainFileForPatternUsages_InCatchClause =
    """object Usage {
      |  try ??? catch {
      |    case _: MyClass =>
      |    case MyObject =>
      |  }
      |}
      |""".stripMargin

  def testUsageInPatterns_InCatchClause(): Unit = {
    myFixture.addFileToProject("definitions.scala", CommonDeclarationsFoPatternUsages)
    doTest(
      MainFileForPatternUsages_InCatchClause,
      """scala.FILE
        |  ScObject[Usage]
        |    extends block
        |      template body
        |        try statement
        |          Reference expression[???] -> Value read
        |          catch block
        |            block of expressions
        |              {
        |              case clauses
        |                case clause
        |                  typed pattern[_] -> Catch clause parameter declaration
        |                    Type pattern -> Catch clause parameter declaration
        |                      simple type -> Catch clause parameter declaration
        |                        reference[MyClass] -> Catch clause parameter declaration
        |                case clause
        |                  StableElementPattern -> Catch clause parameter declaration
        |                    Reference expression[MyObject] -> Catch clause parameter declaration
        |              }
        |""".stripMargin
    )
  }
}
