package org.jetbrains.plugins.scala.lang.findUsages
import org.jetbrains.plugins.scala.ScalaVersion

class ScalaUsageTypeProviderTest_Scala3 extends ScalaUsageTypeProviderTest_Scala2 {

  override protected def supportedIn(version: ScalaVersion): Boolean = version.isScala3

  def testUniversalApplySyntax(): Unit = {
    myFixture.addFileToProject(
      "definitions.scala",
      """class MyClassWithEmptyConstructor()
        |class MyClassWithNonEmptyConstructor(p: String)
        |class MyClassWithMultipleConstructors(p: String) {
        |  def this() = this("42")
        |  def this(i: Int) = this(i.toString)
        |}
        |
        |class MyClassWithMultipleConstructorsAndApplyMethodsInCompanion(p: String) {
        |  def this() = this("42")
        |  def this(i: Int) = this(i.toString)
        |}
        |object MyClassWithMultipleConstructorsAndApplyMethodsInCompanion {
        |  def apply(i: Int, s: String): MyClassWithMultipleConstructorsAndApplyMethodsInCompanion = ???
        |}
        |""".stripMargin
    )

    doTest(
      """new MyClassWithEmptyConstructor()
        |MyClassWithEmptyConstructor()
        |
        |new MyClassWithNonEmptyConstructor("42")
        |MyClassWithNonEmptyConstructor("42")
        |
        |new MyClassWithMultipleConstructors()
        |new MyClassWithMultipleConstructors("42")
        |new MyClassWithMultipleConstructors(23)
        |MyClassWithMultipleConstructors()
        |MyClassWithMultipleConstructors("42")
        |MyClassWithMultipleConstructors(23)
        |
        |new MyClassWithMultipleConstructorsAndApplyMethodsInCompanion()
        |new MyClassWithMultipleConstructorsAndApplyMethodsInCompanion("42")
        |new MyClassWithMultipleConstructorsAndApplyMethodsInCompanion(23)
        |//Invalid code, "constructor proxy" are not generated in this case (see https://docs.scala-lang.org/scala3/reference/other-new-features/creator-applications.html)
        |//MyClassWithMultipleConstructorsAndApplyMethodsInCompanion()
        |//MyClassWithMultipleConstructorsAndApplyMethodsInCompanion("42")
        |//MyClassWithMultipleConstructorsAndApplyMethodsInCompanion(23)
        |MyClassWithMultipleConstructorsAndApplyMethodsInCompanion(23, "42")
        |MyClassWithMultipleConstructorsAndApplyMethodsInCompanion.apply(23, "42")
        |""".stripMargin,
      """scala.FILE
        |  ScNewTemplateDefinition[<anonymous>]
        |    extends block
        |      template parents
        |        constructor -> New instance creation
        |          simple type -> New instance creation
        |            reference[MyClassWithEmptyConstructor] -> New instance creation
        |          arguments of function -> Value read
        |  Method call
        |    Reference expression[MyClassWithEmptyConstructor] -> New instance creation
        |    arguments of function -> Value read
        |  ScNewTemplateDefinition[<anonymous>]
        |    extends block
        |      template parents
        |        constructor -> New instance creation
        |          simple type -> New instance creation
        |            reference[MyClassWithNonEmptyConstructor] -> New instance creation
        |          arguments of function -> Value read
        |            StringLiteral -> Value read
        |  Method call
        |    Reference expression[MyClassWithNonEmptyConstructor] -> New instance creation
        |    arguments of function -> Value read
        |      StringLiteral -> Value read
        |  ScNewTemplateDefinition[<anonymous>]
        |    extends block
        |      template parents
        |        constructor -> New instance creation
        |          simple type -> New instance creation
        |            reference[MyClassWithMultipleConstructors] -> New instance creation
        |          arguments of function -> Value read
        |  ScNewTemplateDefinition[<anonymous>]
        |    extends block
        |      template parents
        |        constructor -> New instance creation
        |          simple type -> New instance creation
        |            reference[MyClassWithMultipleConstructors] -> New instance creation
        |          arguments of function -> Value read
        |            StringLiteral -> Value read
        |  ScNewTemplateDefinition[<anonymous>]
        |    extends block
        |      template parents
        |        constructor -> New instance creation
        |          simple type -> New instance creation
        |            reference[MyClassWithMultipleConstructors] -> New instance creation
        |          arguments of function -> Value read
        |            IntegerLiteral -> Value read
        |  Method call
        |    Reference expression[MyClassWithMultipleConstructors] -> New instance creation
        |    arguments of function -> Value read
        |  Method call
        |    Reference expression[MyClassWithMultipleConstructors] -> New instance creation
        |    arguments of function -> Value read
        |      StringLiteral -> Value read
        |  Method call
        |    Reference expression[MyClassWithMultipleConstructors] -> New instance creation
        |    arguments of function -> Value read
        |      IntegerLiteral -> Value read
        |  ScNewTemplateDefinition[<anonymous>]
        |    extends block
        |      template parents
        |        constructor -> New instance creation
        |          simple type -> New instance creation
        |            reference[MyClassWithMultipleConstructorsAndApplyMethodsInCompanion] -> New instance creation
        |          arguments of function -> Value read
        |  ScNewTemplateDefinition[<anonymous>]
        |    extends block
        |      template parents
        |        constructor -> New instance creation
        |          simple type -> New instance creation
        |            reference[MyClassWithMultipleConstructorsAndApplyMethodsInCompanion] -> New instance creation
        |          arguments of function -> Value read
        |            StringLiteral -> Value read
        |  ScNewTemplateDefinition[<anonymous>]
        |    extends block
        |      template parents
        |        constructor -> New instance creation
        |          simple type -> New instance creation
        |            reference[MyClassWithMultipleConstructorsAndApplyMethodsInCompanion] -> New instance creation
        |          arguments of function -> Value read
        |            IntegerLiteral -> Value read
        |  comment
        |  comment
        |  comment
        |  comment
        |  Method call
        |    Reference expression[MyClassWithMultipleConstructorsAndApplyMethodsInCompanion] -> Method `apply`
        |    arguments of function -> Value read
        |      IntegerLiteral -> Value read
        |      StringLiteral -> Value read
        |  Method call
        |    Reference expression[apply] -> Method `apply`
        |      Reference expression[MyClassWithMultipleConstructorsAndApplyMethodsInCompanion] -> Method `apply`
        |    arguments of function -> Value read
        |      IntegerLiteral -> Value read
        |      StringLiteral -> Value read
        |""".stripMargin
    )
  }
}
