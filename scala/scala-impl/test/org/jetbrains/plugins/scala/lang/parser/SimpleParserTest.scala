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

  // EA-246946
  def test_unfinished_id_list(): Unit = checkTree(
    """
      |var x, = 3
      |""".stripMargin,
    """
      |ScalaFile
      |  PsiWhiteSpace('\n')
      |  ScVariableDefinition: x
      |    AnnotationsList
      |      <empty list>
      |    Modifiers
      |      <empty list>
      |    PsiElement(var)('var')
      |    PsiWhiteSpace(' ')
      |    ListOfPatterns
      |      ReferencePattern: x
      |        PsiElement(identifier)('x')
      |      PsiElement(,)(',')
      |      PsiErrorElement:Expected another pattern
      |        <empty list>
      |    PsiWhiteSpace(' ')
      |    PsiElement(=)('=')
      |    PsiWhiteSpace(' ')
      |    IntegerLiteral
      |      PsiElement(integer)('3')
      |  PsiWhiteSpace('\n')
      |""".stripMargin
  )

  // SCL-16840
  def test_semicolon_error_after_first_statement(): Unit = checkTree(
    """
      |def test(): Unit = {
      |  //val dummy: String = ??? // line #1: commenting this line ...
      |
      |  if (true) {
      |    ???
      |  } if (false) { // line #2: ... will show the error here
      |    ???
      |  }
      |}
      |""".stripMargin.trim,
    """
      |ScalaFile
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
      |        PsiElement())(')')
      |    PsiElement(:)(':')
      |    PsiWhiteSpace(' ')
      |    SimpleType: Unit
      |      CodeReferenceElement: Unit
      |        PsiElement(identifier)('Unit')
      |    PsiWhiteSpace(' ')
      |    PsiElement(=)('=')
      |    PsiWhiteSpace(' ')
      |    BlockExpression
      |      PsiElement({)('{')
      |      PsiWhiteSpace('\n  ')
      |      PsiComment(comment)('//val dummy: String = ??? // line #1: commenting this line ...')
      |      PsiWhiteSpace('\n\n  ')
      |      IfStatement
      |        PsiElement(if)('if')
      |        PsiWhiteSpace(' ')
      |        PsiElement(()('(')
      |        BooleanLiteral
      |          PsiElement(true)('true')
      |        PsiElement())(')')
      |        PsiWhiteSpace(' ')
      |        BlockExpression
      |          PsiElement({)('{')
      |          PsiWhiteSpace('\n    ')
      |          ReferenceExpression: ???
      |            PsiElement(identifier)('???')
      |          PsiWhiteSpace('\n  ')
      |          PsiElement(})('}')
      |      PsiErrorElement:';' or newline expected
      |        <empty list>
      |      PsiWhiteSpace(' ')
      |      IfStatement
      |        PsiElement(if)('if')
      |        PsiWhiteSpace(' ')
      |        PsiElement(()('(')
      |        BooleanLiteral
      |          PsiElement(false)('false')
      |        PsiElement())(')')
      |        PsiWhiteSpace(' ')
      |        BlockExpression
      |          PsiElement({)('{')
      |          PsiWhiteSpace(' ')
      |          PsiComment(comment)('// line #2: ... will show the error here')
      |          PsiWhiteSpace('\n    ')
      |          ReferenceExpression: ???
      |            PsiElement(identifier)('???')
      |          PsiWhiteSpace('\n  ')
      |          PsiElement(})('}')
      |      PsiWhiteSpace('\n')
      |      PsiElement(})('}')
      |""".stripMargin
  )

  def test_annotations_without_target(): Unit = checkTree(
    """
      |object Test {
      |  def test = {
      |    @a
      |  }
      |  @b
      |}
      |@c
      |""".stripMargin.trim,
    """
      |ScalaFile
      |  ScObject: Test
      |    AnnotationsList
      |      <empty list>
      |    Modifiers
      |      <empty list>
      |    PsiElement(object)('object')
      |    PsiWhiteSpace(' ')
      |    PsiElement(identifier)('Test')
      |    PsiWhiteSpace(' ')
      |    ExtendsBlock
      |      ScTemplateBody
      |        PsiElement({)('{')
      |        PsiWhiteSpace('\n  ')
      |        ScFunctionDefinition: test
      |          AnnotationsList
      |            <empty list>
      |          Modifiers
      |            <empty list>
      |          PsiElement(def)('def')
      |          PsiWhiteSpace(' ')
      |          PsiElement(identifier)('test')
      |          Parameters
      |            <empty list>
      |          PsiWhiteSpace(' ')
      |          PsiElement(=)('=')
      |          PsiWhiteSpace(' ')
      |          BlockExpression
      |            PsiElement({)('{')
      |            PsiWhiteSpace('\n    ')
      |            Annotation
      |              PsiElement(@)('@')
      |              AnnotationExpression
      |                ConstructorInvocation
      |                  SimpleType: a
      |                    CodeReferenceElement: a
      |                      PsiElement(identifier)('a')
      |            PsiErrorElement:Missing statement for annotation
      |              <empty list>
      |            PsiWhiteSpace('\n  ')
      |            PsiElement(})('}')
      |        PsiWhiteSpace('\n  ')
      |        Annotation
      |          PsiElement(@)('@')
      |          AnnotationExpression
      |            ConstructorInvocation
      |              SimpleType: b
      |                CodeReferenceElement: b
      |                  PsiElement(identifier)('b')
      |        PsiErrorElement:Missing statement for annotation
      |          <empty list>
      |        PsiWhiteSpace('\n')
      |        PsiElement(})('}')
      |  PsiWhiteSpace('\n')
      |  Annotation
      |    PsiElement(@)('@')
      |    AnnotationExpression
      |      ConstructorInvocation
      |        SimpleType: c
      |          CodeReferenceElement: c
      |            PsiElement(identifier)('c')
      |  PsiErrorElement:Missing statement for annotation
      |    <empty list>
      |""".stripMargin
  )

  def test_line_comment_in_interpolated_string(): Unit = {
    val stringDelim = "\"\"\""
    checkTree(
      s"""
        |s$stringDelim
        | $${ /* blub */ }
        | $${
        |   id // comment
        | }
        |$stringDelim
        |""".stripMargin,
      s"""
        |ScalaFile
        |  PsiWhiteSpace('\\n')
        |  InterpolatedStringLiteral
        |    InterpolatedExpressionPrefix: s
        |      PsiElement(interpolated string id)('s')
        |    PsiElement(interpolated multiline string)('\"\"\"\\n ')
        |    PsiElement(interpolated string injection)('$$')
        |    BlockExpression
        |      PsiElement({)('{')
        |      PsiWhiteSpace(' ')
        |      PsiComment(BlockComment)('/* blub */')
        |      PsiWhiteSpace(' ')
        |      PsiElement(})('}')
        |    PsiElement(interpolated multiline string)('\\n ')
        |    PsiElement(interpolated string injection)('$$')
        |    BlockExpression
        |      PsiElement({)('{')
        |      PsiWhiteSpace('\\n   ')
        |      ReferenceExpression: id
        |        PsiElement(identifier)('id')
        |      PsiWhiteSpace(' ')
        |      PsiComment(comment)('// comment')
        |      PsiWhiteSpace('\\n ')
        |      PsiElement(})('}')
        |    PsiElement(interpolated multiline string)('\\n')
        |    PsiElement(interpolated string end)('\"\"\"')
        |  PsiWhiteSpace('\\n')
        |""".stripMargin
    )
  }

  def test_named_arg_using(): Unit = {
    checkTree(
      """
        |test(using = 42)
        |test(using +3)
        |test(using x y)
        |test(using)
        |""".stripMargin,
      """
        |ScalaFile
        |  PsiWhiteSpace('\n')
        |  MethodCall
        |    ReferenceExpression: test
        |      PsiElement(identifier)('test')
        |    ArgumentList
        |      PsiElement(()('(')
        |      AssignStatement
        |        ReferenceExpression: using
        |          PsiElement(identifier)('using')
        |        PsiWhiteSpace(' ')
        |        PsiElement(=)('=')
        |        PsiWhiteSpace(' ')
        |        IntegerLiteral
        |          PsiElement(integer)('42')
        |      PsiElement())(')')
        |  PsiWhiteSpace('\n')
        |  MethodCall
        |    ReferenceExpression: test
        |      PsiElement(identifier)('test')
        |    ArgumentList
        |      PsiElement(()('(')
        |      PsiElement(using)('using')
        |      PsiWhiteSpace(' ')
        |      PrefixExpression
        |        ReferenceExpression: +
        |          PsiElement(identifier)('+')
        |        IntegerLiteral
        |          PsiElement(integer)('3')
        |      PsiElement())(')')
        |  PsiWhiteSpace('\n')
        |  MethodCall
        |    ReferenceExpression: test
        |      PsiElement(identifier)('test')
        |    ArgumentList
        |      PsiElement(()('(')
        |      PsiElement(using)('using')
        |      PsiWhiteSpace(' ')
        |      PostfixExpression
        |        ReferenceExpression: x
        |          PsiElement(identifier)('x')
        |        PsiWhiteSpace(' ')
        |        ReferenceExpression: y
        |          PsiElement(identifier)('y')
        |      PsiElement())(')')
        |  PsiWhiteSpace('\n')
        |  MethodCall
        |    ReferenceExpression: test
        |      PsiElement(identifier)('test')
        |    ArgumentList
        |      PsiElement(()('(')
        |      ReferenceExpression: using
        |        PsiElement(identifier)('using')
        |      PsiElement())(')')
        |  PsiWhiteSpace('\n')
        |""".stripMargin,
    )
  }
}
