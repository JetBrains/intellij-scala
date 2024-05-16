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

  override def testUsageInPatterns(): Unit = {
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
        |      template body -> Value read
        |        value definition -> Value read
        |          pattern list -> Value read
        |            reference pattern[value] -> Value read
        |          IntegerLiteral -> Value read
        |        match statement -> Value read
        |          Expression in parentheses -> Value read
        |            typed statement -> Value read
        |              Reference expression[???] -> Value read
        |              simple type -> Typed Statement
        |                reference[Any] -> Typed Statement
        |          case clauses -> Value read
        |            case clause -> Value read
        |              Scala 3 Typed Pattern -> Typed Pattern
        |                any sequence -> Typed Pattern
        |                Type pattern -> Typed Pattern
        |                  simple type -> Typed Pattern
        |                    reference[MyClass] -> Typed Pattern
        |            case clause -> Value read
        |              Scala 3 Typed Pattern -> Typed Pattern
        |                any sequence -> Typed Pattern
        |                Type pattern -> Typed Pattern
        |                  simple type -> Typed Pattern
        |                    reference[MyClass] -> Typed Pattern
        |                      reference[example] -> Typed Pattern
        |                        reference[org] -> Typed Pattern
        |            case clause -> Value read
        |              StableElementPattern -> Stable Reference Pattern
        |                Reference expression[MyObject] -> Stable Reference Pattern
        |            case clause -> Value read
        |              StableElementPattern -> Stable Reference Pattern
        |                Reference expression[`value`] -> Stable Reference Pattern
        |            case clause -> Value read
        |              Constructor Pattern -> Extractor
        |                reference[MyClassWithExtractor] -> Extractor
        |                Pattern arguments -> Extractor
        |                  any sequence -> Extractor
        |""".stripMargin
    )
  }

  override def testUsageInPatterns_InCatchClause(): Unit = {
    myFixture.addFileToProject("definitions.scala", CommonDeclarationsFoPatternUsages)
    doTest(
      MainFileForPatternUsages_InCatchClause,
      """scala.FILE
        |  ScObject[Usage]
        |    extends block
        |      template body -> Value read
        |        try statement -> Value read
        |          Reference expression[???] -> Value read
        |          catch block -> Value read
        |            block of expressions -> Value read
        |              { -> Value read
        |              case clauses -> Value read
        |                case clause -> Value read
        |                  Scala 3 Typed Pattern -> Catch clause parameter declaration
        |                    any sequence -> Catch clause parameter declaration
        |                    Type pattern -> Catch clause parameter declaration
        |                      simple type -> Catch clause parameter declaration
        |                        reference[MyClass] -> Catch clause parameter declaration
        |                case clause -> Value read
        |                  StableElementPattern -> Catch clause parameter declaration
        |                    Reference expression[MyObject] -> Catch clause parameter declaration
        |              } -> Value read
        |""".stripMargin
    )
  }

}
