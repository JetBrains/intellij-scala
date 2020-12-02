package org.jetbrains.plugins.scala.lang.parser

class SimpleParserTest extends SimpleScalaParserTestBase {
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

  // SCL-18498
  def test_new_lines_after_typed_statements(): Unit = checkParseErrors(
    """
      |import scala.annotation.nowarn
      |
      |class Main {
      |
      |  (null: String): @nowarn
      |
      |  override def toString: String = "hello"
      |
      |  def foo1: String = {
      |    (null: String): @nowarn
      |
      |  }
      |
      |  def foo2(): Unit = {
      |    (null: String): @nowarn
      |    def bar1():Unit  = ???
      |    (null: String): @nowarn
      |    val x = 42
      |
      |    (null: String): @nowarn
      |
      |    def bar2():Unit  = ???
      |
      |    (null: String): @nowarn
      |
      |    val y = 42
      |
      |    (null: String): @nowarn
      |
      |    (null:
      |
      |    @nowarn
      |
      |    @nowarn
      |
      |    )
      |  }
      |}
      |""".stripMargin
  )
}
