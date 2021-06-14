package org.jetbrains.plugins.scala.lang.parser.scala3

class Scala3UsingParserTest extends SimpleScala3ParserTestBase {

  def test_multi_using_param_clause(): Unit = checkTree(
    """
      |def test(using i: Int)(j: Int)(using k: Int): Unit = ()
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
      |        PsiElement(using)('using')
      |        PsiWhiteSpace(' ')
      |        Parameter: i
      |          AnnotationsList
      |            <empty list>
      |          Modifiers
      |            <empty list>
      |          PsiElement(identifier)('i')
      |          PsiElement(:)(':')
      |          PsiWhiteSpace(' ')
      |          ParameterType
      |            SimpleType: Int
      |              CodeReferenceElement: Int
      |                PsiElement(identifier)('Int')
      |        PsiElement())(')')
      |      ParametersClause
      |        PsiElement(()('(')
      |        Parameter: j
      |          AnnotationsList
      |            <empty list>
      |          Modifiers
      |            <empty list>
      |          PsiElement(identifier)('j')
      |          PsiElement(:)(':')
      |          PsiWhiteSpace(' ')
      |          ParameterType
      |            SimpleType: Int
      |              CodeReferenceElement: Int
      |                PsiElement(identifier)('Int')
      |        PsiElement())(')')
      |      ParametersClause
      |        PsiElement(()('(')
      |        PsiElement(using)('using')
      |        PsiWhiteSpace(' ')
      |        Parameter: k
      |          AnnotationsList
      |            <empty list>
      |          Modifiers
      |            <empty list>
      |          PsiElement(identifier)('k')
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

  def test_multi_using_argument_clause(): Unit = checkTree(
    """
      |test(using 3)(4)(using 5)
      |""".stripMargin,
    """
      |ScalaFile
      |  PsiWhiteSpace('\n')
      |  MethodCall
      |    MethodCall
      |      MethodCall
      |        ReferenceExpression: test
      |          PsiElement(identifier)('test')
      |        ArgumentList
      |          PsiElement(()('(')
      |          PsiElement(using)('using')
      |          PsiWhiteSpace(' ')
      |          IntegerLiteral
      |            PsiElement(integer)('3')
      |          PsiElement())(')')
      |      ArgumentList
      |        PsiElement(()('(')
      |        IntegerLiteral
      |          PsiElement(integer)('4')
      |        PsiElement())(')')
      |    ArgumentList
      |      PsiElement(()('(')
      |      PsiElement(using)('using')
      |      PsiWhiteSpace(' ')
      |      IntegerLiteral
      |        PsiElement(integer)('5')
      |      PsiElement())(')')
      |  PsiWhiteSpace('\n')
      |""".stripMargin
  )

  def test_using_parameters_without_name(): Unit = checkTree(
    """
      |def test(using Int)(using String, Double, ): Unit = ()
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
      |        PsiElement(using)('using')
      |        PsiWhiteSpace(' ')
      |        Parameter: _
      |          ParameterType
      |            SimpleType: Int
      |              CodeReferenceElement: Int
      |                PsiElement(identifier)('Int')
      |        PsiElement())(')')
      |      ParametersClause
      |        PsiElement(()('(')
      |        PsiElement(using)('using')
      |        PsiWhiteSpace(' ')
      |        Parameter: _
      |          ParameterType
      |            SimpleType: String
      |              CodeReferenceElement: String
      |                PsiElement(identifier)('String')
      |        PsiElement(,)(',')
      |        PsiWhiteSpace(' ')
      |        Parameter: _
      |          ParameterType
      |            SimpleType: Double
      |              CodeReferenceElement: Double
      |                PsiElement(identifier)('Double')
      |        PsiElement(,)(',')
      |        PsiErrorElement:Expected more types
      |          <empty list>
      |        PsiWhiteSpace(' ')
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


  def test_class_parameter_clauses(): Unit = checkTree(
    """
      |class Test(using Int, Test)(i: Int)(using val xxx: String, yyy: Int = 3)
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
      |          PsiElement(using)('using')
      |          PsiWhiteSpace(' ')
      |          ClassParameter: _
      |            ParameterType
      |              SimpleType: Int
      |                CodeReferenceElement: Int
      |                  PsiElement(identifier)('Int')
      |          PsiElement(,)(',')
      |          PsiWhiteSpace(' ')
      |          ClassParameter: _
      |            ParameterType
      |              SimpleType: Test
      |                CodeReferenceElement: Test
      |                  PsiElement(identifier)('Test')
      |          PsiElement())(')')
      |        ParametersClause
      |          PsiElement(()('(')
      |          ClassParameter: i
      |            AnnotationsList
      |              <empty list>
      |            Modifiers
      |              <empty list>
      |            PsiElement(identifier)('i')
      |            PsiElement(:)(':')
      |            PsiWhiteSpace(' ')
      |            ParameterType
      |              SimpleType: Int
      |                CodeReferenceElement: Int
      |                  PsiElement(identifier)('Int')
      |          PsiElement())(')')
      |        ParametersClause
      |          PsiElement(()('(')
      |          PsiElement(using)('using')
      |          PsiWhiteSpace(' ')
      |          ClassParameter: xxx
      |            AnnotationsList
      |              <empty list>
      |            Modifiers
      |              <empty list>
      |            PsiElement(val)('val')
      |            PsiWhiteSpace(' ')
      |            PsiElement(identifier)('xxx')
      |            PsiElement(:)(':')
      |            PsiWhiteSpace(' ')
      |            ParameterType
      |              SimpleType: String
      |                CodeReferenceElement: String
      |                  PsiElement(identifier)('String')
      |          PsiElement(,)(',')
      |          PsiWhiteSpace(' ')
      |          ClassParameter: yyy
      |            AnnotationsList
      |              <empty list>
      |            Modifiers
      |              <empty list>
      |            PsiElement(identifier)('yyy')
      |            PsiElement(:)(':')
      |            PsiWhiteSpace(' ')
      |            ParameterType
      |              SimpleType: Int
      |                CodeReferenceElement: Int
      |                  PsiElement(identifier)('Int')
      |            PsiWhiteSpace(' ')
      |            PsiElement(=)('=')
      |            PsiWhiteSpace(' ')
      |            IntegerLiteral
      |              PsiElement(integer)('3')
      |          PsiElement())(')')
      |    ExtendsBlock
      |      <empty list>
      |  PsiWhiteSpace('\n')
      |""".stripMargin
  )
}
