package org.jetbrains.plugins.scala.lang.parser.scala3

class ModifierParserTest extends SimpleScala3ParserTestBase {
  def test_inline_if(): Unit = checkParseErrors(
    "inline if (a > b) 1 else 2"
  )

  def test_inline_match(): Unit = checkParseErrors(
    "inline (1 - 2) * 3 match { case _ => () }"
  )

  def test_local_inline_def(): Unit = checkParseErrors(
    """
      |object Test {
      |  def foo() = {
      |    inline def bar: Int = foo(0)
      |  }
      |}
      |""".stripMargin
  )

  def test_toplevel_inline_def(): Unit = checkTree(
    """
      |package test
      |inline def foo() = ()
      |""".stripMargin,
    """
      |ScalaFile
      |  PsiWhiteSpace('\n')
      |  ScPackaging
      |    PsiElement(package)('package')
      |    PsiWhiteSpace(' ')
      |    CodeReferenceElement: test
      |      PsiElement(identifier)('test')
      |    PsiWhiteSpace('\n')
      |    ScFunctionDefinition: foo
      |      AnnotationsList
      |        <empty list>
      |      Modifiers
      |        PsiElement(inline)('inline')
      |      PsiWhiteSpace(' ')
      |      PsiElement(def)('def')
      |      PsiWhiteSpace(' ')
      |      PsiElement(identifier)('foo')
      |      Parameters
      |        ParametersClause
      |          PsiElement(()('(')
      |          PsiElement())(')')
      |      PsiWhiteSpace(' ')
      |      PsiElement(=)('=')
      |      PsiWhiteSpace(' ')
      |      UnitExpression
      |        PsiElement(()('(')
      |        PsiElement())(')')
      |  PsiWhiteSpace('\n')
      |""".stripMargin
  )

  def test_inline_def_param(): Unit = checkTree(
    """
      |def test(inline i: Int): Unit = ()
      |""".stripMargin,
    """
      |ScalaFile
      |  PsiWhiteSpace('\n')
      |  ScFunctionDefinition: test
      |    AnnotationsList
      |      <empty list>
      |    Modifiers
      |      <empty list>
      |    PsiElement(def)('def')
      |    PsiWhiteSpace(' ')
      |    PsiElement(identifier)('test')
      |    Parameters
      |      ParametersClause
      |        PsiElement(()('(')
      |        Parameter: i
      |          AnnotationsList
      |            <empty list>
      |          Modifiers
      |            PsiElement(inline)('inline')
      |          PsiWhiteSpace(' ')
      |          PsiElement(identifier)('i')
      |          PsiElement(:)(':')
      |          PsiWhiteSpace(' ')
      |          ParameterType
      |            SimpleType: Int
      |              CodeReferenceElement: Int
      |                PsiElement(identifier)('Int')
      |        PsiElement())(')')
      |    PsiElement(:)(':')
      |    PsiWhiteSpace(' ')
      |    SimpleType: Unit
      |      CodeReferenceElement: Unit
      |        PsiElement(identifier)('Unit')
      |    PsiWhiteSpace(' ')
      |    PsiElement(=)('=')
      |    PsiWhiteSpace(' ')
      |    UnitExpression
      |      PsiElement(()('(')
      |      PsiElement())(')')
      |  PsiWhiteSpace('\n')
      |""".stripMargin
  )

  def test_inline_class_parameter(): Unit = checkTree(
    """
      |class Test(inline x: int)
      |""".stripMargin,
    """
      |ScalaFile
      |  PsiWhiteSpace('\n')
      |  ScClass: Test
      |    AnnotationsList
      |      <empty list>
      |    Modifiers
      |      <empty list>
      |    PsiElement(class)('class')
      |    PsiWhiteSpace(' ')
      |    PsiElement(identifier)('Test')
      |    PrimaryConstructor
      |      AnnotationsList
      |        <empty list>
      |      Modifiers
      |        <empty list>
      |      Parameters
      |        ParametersClause
      |          PsiElement(()('(')
      |          ClassParameter: x
      |            AnnotationsList
      |              <empty list>
      |            Modifiers
      |              PsiElement(inline)('inline')
      |            PsiWhiteSpace(' ')
      |            PsiElement(identifier)('x')
      |            PsiElement(:)(':')
      |            PsiWhiteSpace(' ')
      |            ParameterType
      |              SimpleType: int
      |                CodeReferenceElement: int
      |                  PsiElement(identifier)('int')
      |          PsiElement())(')')
      |    ExtendsBlock
      |      <empty list>
      |  PsiWhiteSpace('\n')
      |""".stripMargin
  )

  def test_transparent(): Unit = checkParseErrors(
    """
      |transparent inline def choose(b: Boolean): A =
      |  if b then new A() else new B()
      |""".stripMargin
  )

  def test_open(): Unit = checkParseErrors(
    """
      |open class Test
      |""".stripMargin
  )

  def test_parameter_named_inline(): Unit = checkTree(
    """
      |def test(inline: T) = ()
      |""".stripMargin,
    """
      |ScalaFile
      |  PsiWhiteSpace('\n')
      |  ScFunctionDefinition: test
      |    AnnotationsList
      |      <empty list>
      |    Modifiers
      |      <empty list>
      |    PsiElement(def)('def')
      |    PsiWhiteSpace(' ')
      |    PsiElement(identifier)('test')
      |    Parameters
      |      ParametersClause
      |        PsiElement(()('(')
      |        Parameter: inline
      |          AnnotationsList
      |            <empty list>
      |          Modifiers
      |            <empty list>
      |          PsiElement(identifier)('inline')
      |          PsiElement(:)(':')
      |          PsiWhiteSpace(' ')
      |          ParameterType
      |            SimpleType: T
      |              CodeReferenceElement: T
      |                PsiElement(identifier)('T')
      |        PsiElement())(')')
      |    PsiWhiteSpace(' ')
      |    PsiElement(=)('=')
      |    PsiWhiteSpace(' ')
      |    UnitExpression
      |      PsiElement(()('(')
      |      PsiElement())(')')
      |  PsiWhiteSpace('\n')
      |""".stripMargin
  )

  def test_inline_parameter_named_inline(): Unit = checkTree(
    """
      |def test(inline inline: T) = ()
      |""".stripMargin,
    """
      |ScalaFile
      |  PsiWhiteSpace('\n')
      |  ScFunctionDefinition: test
      |    AnnotationsList
      |      <empty list>
      |    Modifiers
      |      <empty list>
      |    PsiElement(def)('def')
      |    PsiWhiteSpace(' ')
      |    PsiElement(identifier)('test')
      |    Parameters
      |      ParametersClause
      |        PsiElement(()('(')
      |        Parameter: inline
      |          AnnotationsList
      |            <empty list>
      |          Modifiers
      |            PsiElement(inline)('inline')
      |          PsiWhiteSpace(' ')
      |          PsiElement(identifier)('inline')
      |          PsiElement(:)(':')
      |          PsiWhiteSpace(' ')
      |          ParameterType
      |            SimpleType: T
      |              CodeReferenceElement: T
      |                PsiElement(identifier)('T')
      |        PsiElement())(')')
      |    PsiWhiteSpace(' ')
      |    PsiElement(=)('=')
      |    PsiWhiteSpace(' ')
      |    UnitExpression
      |      PsiElement(()('(')
      |      PsiElement())(')')
      |  PsiWhiteSpace('\n')
      |""".stripMargin
  )

  def test_inline_val(): Unit = checkTree(
    """
      |def test = {
      |  inline val x = 3
      |}
      |""".stripMargin,
    """
      |ScalaFile
      |  PsiWhiteSpace('\n')
      |  ScFunctionDefinition: test
      |    AnnotationsList
      |      <empty list>
      |    Modifiers
      |      <empty list>
      |    PsiElement(def)('def')
      |    PsiWhiteSpace(' ')
      |    PsiElement(identifier)('test')
      |    Parameters
      |      <empty list>
      |    PsiWhiteSpace(' ')
      |    PsiElement(=)('=')
      |    PsiWhiteSpace(' ')
      |    BlockExpression
      |      PsiElement({)('{')
      |      PsiWhiteSpace('\n  ')
      |      ScPatternDefinition: x
      |        AnnotationsList
      |          <empty list>
      |        Modifiers
      |          PsiElement(inline)('inline')
      |        PsiWhiteSpace(' ')
      |        PsiElement(val)('val')
      |        PsiWhiteSpace(' ')
      |        ListOfPatterns
      |          ReferencePattern: x
      |            PsiElement(identifier)('x')
      |        PsiWhiteSpace(' ')
      |        PsiElement(=)('=')
      |        PsiWhiteSpace(' ')
      |        IntegerLiteral
      |          PsiElement(integer)('3')
      |      PsiWhiteSpace('\n')
      |      PsiElement(})('}')
      |  PsiWhiteSpace('\n')
      |""".stripMargin
  )
}
