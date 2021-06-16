package org.jetbrains.plugins.scala.lang.parser.scala3

class ExtensionParserTest extends SimpleScala3ParserTestBase {

  def test_simple(): Unit = checkTree(
    """
      |extension (i: Int)
      |  def test = 0
      |""".stripMargin,
    """
      |ScalaFile
      |  PsiWhiteSpace('\n')
      |  Extension on Int
      |    PsiElement(extension)('extension')
      |    PsiWhiteSpace(' ')
      |    Parameters
      |      ParametersClause
      |        PsiElement(()('(')
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
      |    PsiWhiteSpace('\n  ')
      |    ScTemplateBody
      |      ScFunctionDefinition: test
      |        AnnotationsList
      |          <empty list>
      |        Modifiers
      |          <empty list>
      |        PsiElement(def)('def')
      |        PsiWhiteSpace(' ')
      |        PsiElement(identifier)('test')
      |        Parameters
      |          <empty list>
      |        PsiWhiteSpace(' ')
      |        PsiElement(=)('=')
      |        PsiWhiteSpace(' ')
      |        IntegerLiteral
      |          PsiElement(integer)('0')
      |  PsiWhiteSpace('\n')
      |""".stripMargin
  )

  def test_with_using(): Unit = checkTree(
    """
      |extension (i: Int)(using X)
      |  def test = 0
      |""".stripMargin,
    """
      |ScalaFile
      |  PsiWhiteSpace('\n')
      |  Extension on Int
      |    PsiElement(extension)('extension')
      |    PsiWhiteSpace(' ')
      |    Parameters
      |      ParametersClause
      |        PsiElement(()('(')
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
      |        PsiElement(using)('using')
      |        PsiWhiteSpace(' ')
      |        Parameter: _
      |          ParameterType
      |            SimpleType: X
      |              CodeReferenceElement: X
      |                PsiElement(identifier)('X')
      |        PsiElement())(')')
      |    PsiWhiteSpace('\n  ')
      |    ScTemplateBody
      |      ScFunctionDefinition: test
      |        AnnotationsList
      |          <empty list>
      |        Modifiers
      |          <empty list>
      |        PsiElement(def)('def')
      |        PsiWhiteSpace(' ')
      |        PsiElement(identifier)('test')
      |        Parameters
      |          <empty list>
      |        PsiWhiteSpace(' ')
      |        PsiElement(=)('=')
      |        PsiWhiteSpace(' ')
      |        IntegerLiteral
      |          PsiElement(integer)('0')
      |  PsiWhiteSpace('\n')
      |""".stripMargin
  )

  def test_same_line(): Unit = checkTree(
    """
      |extension (i: Int) def test = 0
      |  def not_extension = 1
      |""".stripMargin,
    """
      |ScalaFile
      |  PsiWhiteSpace('\n')
      |  Extension on Int
      |    PsiElement(extension)('extension')
      |    PsiWhiteSpace(' ')
      |    Parameters
      |      ParametersClause
      |        PsiElement(()('(')
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
      |    PsiWhiteSpace(' ')
      |    ScTemplateBody
      |      ScFunctionDefinition: test
      |        AnnotationsList
      |          <empty list>
      |        Modifiers
      |          <empty list>
      |        PsiElement(def)('def')
      |        PsiWhiteSpace(' ')
      |        PsiElement(identifier)('test')
      |        Parameters
      |          <empty list>
      |        PsiWhiteSpace(' ')
      |        PsiElement(=)('=')
      |        PsiWhiteSpace(' ')
      |        IntegerLiteral
      |          PsiElement(integer)('0')
      |  PsiWhiteSpace('\n  ')
      |  ScFunctionDefinition: not_extension
      |    AnnotationsList
      |      <empty list>
      |    Modifiers
      |      <empty list>
      |    PsiElement(def)('def')
      |    PsiWhiteSpace(' ')
      |    PsiElement(identifier)('not_extension')
      |    Parameters
      |      <empty list>
      |    PsiWhiteSpace(' ')
      |    PsiElement(=)('=')
      |    PsiWhiteSpace(' ')
      |    IntegerLiteral
      |      PsiElement(integer)('1')
      |  PsiWhiteSpace('\n')
      |""".stripMargin
  )

  def test_block(): Unit = checkTree(
    """
      |extension (i: Int) {
      |  def a = 0
      |  def b = 0
      |}
      |""".stripMargin,
    """
      |ScalaFile
      |  PsiWhiteSpace('\n')
      |  Extension on Int
      |    PsiElement(extension)('extension')
      |    PsiWhiteSpace(' ')
      |    Parameters
      |      ParametersClause
      |        PsiElement(()('(')
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
      |    PsiWhiteSpace(' ')
      |    ScTemplateBody
      |      PsiElement({)('{')
      |      PsiWhiteSpace('\n  ')
      |      ScFunctionDefinition: a
      |        AnnotationsList
      |          <empty list>
      |        Modifiers
      |          <empty list>
      |        PsiElement(def)('def')
      |        PsiWhiteSpace(' ')
      |        PsiElement(identifier)('a')
      |        Parameters
      |          <empty list>
      |        PsiWhiteSpace(' ')
      |        PsiElement(=)('=')
      |        PsiWhiteSpace(' ')
      |        IntegerLiteral
      |          PsiElement(integer)('0')
      |      PsiWhiteSpace('\n  ')
      |      ScFunctionDefinition: b
      |        AnnotationsList
      |          <empty list>
      |        Modifiers
      |          <empty list>
      |        PsiElement(def)('def')
      |        PsiWhiteSpace(' ')
      |        PsiElement(identifier)('b')
      |        Parameters
      |          <empty list>
      |        PsiWhiteSpace(' ')
      |        PsiElement(=)('=')
      |        PsiWhiteSpace(' ')
      |        IntegerLiteral
      |          PsiElement(integer)('0')
      |      PsiWhiteSpace('\n')
      |      PsiElement(})('}')
      |  PsiWhiteSpace('\n')
      |""".stripMargin
  )

  def test_multi(): Unit = checkTree(
    """
      |extension (i: Int)
      |  def a = 0
      |  def b = 0
      |""".stripMargin,
    """
      |ScalaFile
      |  PsiWhiteSpace('\n')
      |  Extension on Int
      |    PsiElement(extension)('extension')
      |    PsiWhiteSpace(' ')
      |    Parameters
      |      ParametersClause
      |        PsiElement(()('(')
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
      |    PsiWhiteSpace('\n  ')
      |    ScTemplateBody
      |      ScFunctionDefinition: a
      |        AnnotationsList
      |          <empty list>
      |        Modifiers
      |          <empty list>
      |        PsiElement(def)('def')
      |        PsiWhiteSpace(' ')
      |        PsiElement(identifier)('a')
      |        Parameters
      |          <empty list>
      |        PsiWhiteSpace(' ')
      |        PsiElement(=)('=')
      |        PsiWhiteSpace(' ')
      |        IntegerLiteral
      |          PsiElement(integer)('0')
      |      PsiWhiteSpace('\n  ')
      |      ScFunctionDefinition: b
      |        AnnotationsList
      |          <empty list>
      |        Modifiers
      |          <empty list>
      |        PsiElement(def)('def')
      |        PsiWhiteSpace(' ')
      |        PsiElement(identifier)('b')
      |        Parameters
      |          <empty list>
      |        PsiWhiteSpace(' ')
      |        PsiElement(=)('=')
      |        PsiWhiteSpace(' ')
      |        IntegerLiteral
      |          PsiElement(integer)('0')
      |  PsiWhiteSpace('\n')
      |""".stripMargin
  )

  def test_multi_with_colon(): Unit = checkTree(
    """
      |extension (i: Int):
      |  def a = 0
      |  def b = 0
      |""".stripMargin,
    """
      |ScalaFile
      |  PsiWhiteSpace('\n')
      |  Extension on Int
      |    PsiElement(extension)('extension')
      |    PsiWhiteSpace(' ')
      |    Parameters
      |      ParametersClause
      |        PsiElement(()('(')
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
      |    ScTemplateBody
      |      PsiElement(:)(':')
      |      PsiWhiteSpace('\n  ')
      |      ScFunctionDefinition: a
      |        AnnotationsList
      |          <empty list>
      |        Modifiers
      |          <empty list>
      |        PsiElement(def)('def')
      |        PsiWhiteSpace(' ')
      |        PsiElement(identifier)('a')
      |        Parameters
      |          <empty list>
      |        PsiWhiteSpace(' ')
      |        PsiElement(=)('=')
      |        PsiWhiteSpace(' ')
      |        IntegerLiteral
      |          PsiElement(integer)('0')
      |      PsiWhiteSpace('\n  ')
      |      ScFunctionDefinition: b
      |        AnnotationsList
      |          <empty list>
      |        Modifiers
      |          <empty list>
      |        PsiElement(def)('def')
      |        PsiWhiteSpace(' ')
      |        PsiElement(identifier)('b')
      |        Parameters
      |          <empty list>
      |        PsiWhiteSpace(' ')
      |        PsiElement(=)('=')
      |        PsiWhiteSpace(' ')
      |        IntegerLiteral
      |          PsiElement(integer)('0')
      |  PsiWhiteSpace('\n')
      |""".stripMargin
  )

  def test_end(): Unit = checkTree(
    """
      |extension (i: Int)
      |  def a = 0
      |  def b = 1
      |end extension
      |""".stripMargin,
    """
      |ScalaFile
      |  PsiWhiteSpace('\n')
      |  Extension on Int
      |    PsiElement(extension)('extension')
      |    PsiWhiteSpace(' ')
      |    Parameters
      |      ParametersClause
      |        PsiElement(()('(')
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
      |    PsiWhiteSpace('\n  ')
      |    ScTemplateBody
      |      ScFunctionDefinition: a
      |        AnnotationsList
      |          <empty list>
      |        Modifiers
      |          <empty list>
      |        PsiElement(def)('def')
      |        PsiWhiteSpace(' ')
      |        PsiElement(identifier)('a')
      |        Parameters
      |          <empty list>
      |        PsiWhiteSpace(' ')
      |        PsiElement(=)('=')
      |        PsiWhiteSpace(' ')
      |        IntegerLiteral
      |          PsiElement(integer)('0')
      |      PsiWhiteSpace('\n  ')
      |      ScFunctionDefinition: b
      |        AnnotationsList
      |          <empty list>
      |        Modifiers
      |          <empty list>
      |        PsiElement(def)('def')
      |        PsiWhiteSpace(' ')
      |        PsiElement(identifier)('b')
      |        Parameters
      |          <empty list>
      |        PsiWhiteSpace(' ')
      |        PsiElement(=)('=')
      |        PsiWhiteSpace(' ')
      |        IntegerLiteral
      |          PsiElement(integer)('1')
      |      PsiWhiteSpace('\n')
      |      End: extension
      |        PsiElement(end)('end')
      |        PsiWhiteSpace(' ')
      |        PsiElement(identifier)('extension')
      |  PsiWhiteSpace('\n')
      |""".stripMargin
  )


  def test_end_empty_template_body(): Unit = checkTree(
    """extension (x: String)
      |end extension""".stripMargin,
    """ScalaFile
      |  Extension on String
      |    PsiElement(extension)('extension')
      |    PsiWhiteSpace(' ')
      |    Parameters
      |      ParametersClause
      |        PsiElement(()('(')
      |        Parameter: x
      |          AnnotationsList
      |            <empty list>
      |          Modifiers
      |            <empty list>
      |          PsiElement(identifier)('x')
      |          PsiElement(:)(':')
      |          PsiWhiteSpace(' ')
      |          ParameterType
      |            SimpleType: String
      |              CodeReferenceElement: String
      |                PsiElement(identifier)('String')
      |        PsiElement())(')')
      |    PsiWhiteSpace('\n')
      |    ScTemplateBody
      |      PsiErrorElement:Expected at least one extension method
      |        <empty list>
      |      End: extension
      |        PsiElement(end)('end')
      |        PsiWhiteSpace(' ')
      |        PsiElement(identifier)('extension')""".stripMargin
  )

  // #EA-5880432
  def test_wrong_extension(): Unit = checkTree(
    """
      |trait Error01 {
      |  extension []
      |}
      |""".stripMargin,
    """
      |ScalaFile
      |  PsiWhiteSpace('\n')
      |  ScTrait: Error01
      |    AnnotationsList
      |      <empty list>
      |    Modifiers
      |      <empty list>
      |    PsiElement(trait)('trait')
      |    PsiWhiteSpace(' ')
      |    PsiElement(identifier)('Error01')
      |    PsiWhiteSpace(' ')
      |    ExtendsBlock
      |      ScTemplateBody
      |        PsiElement({)('{')
      |        PsiWhiteSpace('\n  ')
      |        Extension on <unknown>
      |          PsiElement(extension)('extension')
      |          PsiWhiteSpace(' ')
      |          TypeParameterClause
      |            PsiElement([)('[')
      |            PsiErrorElement:Wrong parameter
      |              <empty list>
      |            PsiElement(])(']')
      |          Parameters
      |            PsiErrorElement:Parameter clause expected
      |              <empty list>
      |          ScTemplateBody
      |            <empty list>
      |        PsiWhiteSpace('\n')
      |        PsiElement(})('}')
      |  PsiWhiteSpace('\n')
      |""".stripMargin
  )
}
