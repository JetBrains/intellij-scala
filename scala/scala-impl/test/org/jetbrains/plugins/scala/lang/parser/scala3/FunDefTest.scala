package org.jetbrains.plugins.scala.lang.parser.scala3

class FunDefTest extends SimpleScala3ParserTestBase {

  def test_fun_def_not_indented(): Unit = checkTree(
    """def foo=
      |println("foo 1")
      |println("foo 2")
      |
      |class A {
      |  def foo=
      |  println("foo 1")
      |  println("foo 2")
      |}""".stripMargin,
    """ScalaFile
      |  ScFunctionDefinition: foo
      |    AnnotationsList
      |      <empty list>
      |    Modifiers
      |      <empty list>
      |    PsiElement(def)('def')
      |    PsiWhiteSpace(' ')
      |    PsiElement(identifier)('foo')
      |    Parameters
      |      <empty list>
      |    PsiElement(=)('=')
      |    PsiWhiteSpace('\n')
      |    MethodCall
      |      ReferenceExpression: println
      |        PsiElement(identifier)('println')
      |      ArgumentList
      |        PsiElement(()('(')
      |        StringLiteral
      |          PsiElement(string content)('"foo 1"')
      |        PsiElement())(')')
      |  PsiWhiteSpace('\n')
      |  MethodCall
      |    ReferenceExpression: println
      |      PsiElement(identifier)('println')
      |    ArgumentList
      |      PsiElement(()('(')
      |      StringLiteral
      |        PsiElement(string content)('"foo 2"')
      |      PsiElement())(')')
      |  PsiWhiteSpace('\n\n')
      |  ScClass: A
      |    AnnotationsList
      |      <empty list>
      |    Modifiers
      |      <empty list>
      |    PsiElement(class)('class')
      |    PsiWhiteSpace(' ')
      |    PsiElement(identifier)('A')
      |    PrimaryConstructor
      |      AnnotationsList
      |        <empty list>
      |      Modifiers
      |        <empty list>
      |      Parameters
      |        <empty list>
      |    PsiWhiteSpace(' ')
      |    ExtendsBlock
      |      ScTemplateBody
      |        PsiElement({)('{')
      |        PsiWhiteSpace('\n  ')
      |        ScFunctionDefinition: foo
      |          AnnotationsList
      |            <empty list>
      |          Modifiers
      |            <empty list>
      |          PsiElement(def)('def')
      |          PsiWhiteSpace(' ')
      |          PsiElement(identifier)('foo')
      |          Parameters
      |            <empty list>
      |          PsiElement(=)('=')
      |          PsiWhiteSpace('\n  ')
      |          MethodCall
      |            ReferenceExpression: println
      |              PsiElement(identifier)('println')
      |            ArgumentList
      |              PsiElement(()('(')
      |              StringLiteral
      |                PsiElement(string content)('"foo 1"')
      |              PsiElement())(')')
      |        PsiWhiteSpace('\n  ')
      |        MethodCall
      |          ReferenceExpression: println
      |            PsiElement(identifier)('println')
      |          ArgumentList
      |            PsiElement(()('(')
      |            StringLiteral
      |              PsiElement(string content)('"foo 2"')
      |            PsiElement())(')')
      |        PsiWhiteSpace('\n')
      |        PsiElement(})('}')""".stripMargin
  )

  def test_fun_def_not_indented_unindented(): Unit = checkTree(
    """class A {
      |  def foo=
      | println("foo 1")
      |  println("foo 2")
      |}""".stripMargin,
    """ScalaFile
      |  ScClass: A
      |    AnnotationsList
      |      <empty list>
      |    Modifiers
      |      <empty list>
      |    PsiElement(class)('class')
      |    PsiWhiteSpace(' ')
      |    PsiElement(identifier)('A')
      |    PrimaryConstructor
      |      AnnotationsList
      |        <empty list>
      |      Modifiers
      |        <empty list>
      |      Parameters
      |        <empty list>
      |    PsiWhiteSpace(' ')
      |    ExtendsBlock
      |      ScTemplateBody
      |        PsiElement({)('{')
      |        PsiWhiteSpace('\n  ')
      |        ScFunctionDefinition: foo
      |          AnnotationsList
      |            <empty list>
      |          Modifiers
      |            <empty list>
      |          PsiElement(def)('def')
      |          PsiWhiteSpace(' ')
      |          PsiElement(identifier)('foo')
      |          Parameters
      |            <empty list>
      |          PsiElement(=)('=')
      |          PsiWhiteSpace('\n ')
      |          MethodCall
      |            ReferenceExpression: println
      |              PsiElement(identifier)('println')
      |            ArgumentList
      |              PsiElement(()('(')
      |              StringLiteral
      |                PsiElement(string content)('"foo 1"')
      |              PsiElement())(')')
      |        PsiWhiteSpace('\n  ')
      |        MethodCall
      |          ReferenceExpression: println
      |            PsiElement(identifier)('println')
      |          ArgumentList
      |            PsiElement(()('(')
      |            StringLiteral
      |              PsiElement(string content)('"foo 2"')
      |            PsiElement())(')')
      |        PsiWhiteSpace('\n')
      |        PsiElement(})('}')""".stripMargin
  )

  def test_fun_return_with_indention_based_block(): Unit = checkTree(
    """def foo123: String =
      |  return
      |    val x = 1
      |    val y = 2
      |    (x + y).toString""".stripMargin,
    """ScalaFile
      |  ScFunctionDefinition: foo123
      |    AnnotationsList
      |      <empty list>
      |    Modifiers
      |      <empty list>
      |    PsiElement(def)('def')
      |    PsiWhiteSpace(' ')
      |    PsiElement(identifier)('foo123')
      |    Parameters
      |      <empty list>
      |    PsiElement(:)(':')
      |    PsiWhiteSpace(' ')
      |    SimpleType: String
      |      CodeReferenceElement: String
      |        PsiElement(identifier)('String')
      |    PsiWhiteSpace(' ')
      |    PsiElement(=)('=')
      |    PsiWhiteSpace('\n  ')
      |    ReturnStatement
      |      PsiElement(return)('return')
      |      BlockExpression
      |        PsiWhiteSpace('\n    ')
      |        ScPatternDefinition: x
      |          AnnotationsList
      |            <empty list>
      |          Modifiers
      |            <empty list>
      |          PsiElement(val)('val')
      |          PsiWhiteSpace(' ')
      |          ListOfPatterns
      |            ReferencePattern: x
      |              PsiElement(identifier)('x')
      |          PsiWhiteSpace(' ')
      |          PsiElement(=)('=')
      |          PsiWhiteSpace(' ')
      |          IntegerLiteral
      |            PsiElement(integer)('1')
      |        PsiWhiteSpace('\n    ')
      |        ScPatternDefinition: y
      |          AnnotationsList
      |            <empty list>
      |          Modifiers
      |            <empty list>
      |          PsiElement(val)('val')
      |          PsiWhiteSpace(' ')
      |          ListOfPatterns
      |            ReferencePattern: y
      |              PsiElement(identifier)('y')
      |          PsiWhiteSpace(' ')
      |          PsiElement(=)('=')
      |          PsiWhiteSpace(' ')
      |          IntegerLiteral
      |            PsiElement(integer)('2')
      |        PsiWhiteSpace('\n    ')
      |        ReferenceExpression: (x + y).toString
      |          ExpressionInParenthesis
      |            PsiElement(()('(')
      |            InfixExpression
      |              ReferenceExpression: x
      |                PsiElement(identifier)('x')
      |              PsiWhiteSpace(' ')
      |              ReferenceExpression: +
      |                PsiElement(identifier)('+')
      |              PsiWhiteSpace(' ')
      |              ReferenceExpression: y
      |                PsiElement(identifier)('y')
      |            PsiElement())(')')
      |          PsiElement(.)('.')
      |          PsiElement(identifier)('toString')""".stripMargin
  )

  def test_func_expr_result(): Unit = checkTree(
    """
      |def error =
      |  x
      |  () => ()
      |
      |def test = 3
      |""".stripMargin,
    """
      |ScalaFile
      |  PsiWhiteSpace('\n')
      |  ScFunctionDefinition: error
      |    AnnotationsList
      |      <empty list>
      |    Modifiers
      |      <empty list>
      |    PsiElement(def)('def')
      |    PsiWhiteSpace(' ')
      |    PsiElement(identifier)('error')
      |    Parameters
      |      <empty list>
      |    PsiWhiteSpace(' ')
      |    PsiElement(=)('=')
      |    BlockExpression
      |      PsiWhiteSpace('\n  ')
      |      ReferenceExpression: x
      |        PsiElement(identifier)('x')
      |      PsiWhiteSpace('\n  ')
      |      FunctionExpression
      |        Parameters
      |          ParametersClause
      |            PsiElement(()('(')
      |            PsiElement())(')')
      |        PsiWhiteSpace(' ')
      |        PsiElement(=>)('=>')
      |        PsiWhiteSpace(' ')
      |        BlockOfExpressions
      |          UnitExpression
      |            PsiElement(()('(')
      |            PsiElement())(')')
      |  PsiWhiteSpace('\n\n')
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
      |    IntegerLiteral
      |      PsiElement(integer)('3')
      |  PsiWhiteSpace('\n')
      |""".stripMargin
  )

  def test_fn_block_starting_with_a_block_comment_followed_by_fn(): Unit = checkTree(
    """def foo =
      |  /* some comment */ inline def a = 21
      |  /* another comment */ inline def b = 35
      |  println(a + b)
      |""".stripMargin,
    """ScalaFile
      |  ScFunctionDefinition: foo
      |    AnnotationsList
      |      <empty list>
      |    Modifiers
      |      <empty list>
      |    PsiElement(def)('def')
      |    PsiWhiteSpace(' ')
      |    PsiElement(identifier)('foo')
      |    Parameters
      |      <empty list>
      |    PsiWhiteSpace(' ')
      |    PsiElement(=)('=')
      |    BlockExpression
      |      PsiWhiteSpace('\n  ')
      |      ScFunctionDefinition: a
      |        PsiComment(BlockComment)('/* some comment */')
      |        PsiWhiteSpace(' ')
      |        AnnotationsList
      |          <empty list>
      |        Modifiers
      |          PsiElement(inline)('inline')
      |        PsiWhiteSpace(' ')
      |        PsiElement(def)('def')
      |        PsiWhiteSpace(' ')
      |        PsiElement(identifier)('a')
      |        Parameters
      |          <empty list>
      |        PsiWhiteSpace(' ')
      |        PsiElement(=)('=')
      |        PsiWhiteSpace(' ')
      |        IntegerLiteral
      |          PsiElement(integer)('21')
      |      PsiWhiteSpace('\n  ')
      |      ScFunctionDefinition: b
      |        PsiComment(BlockComment)('/* another comment */')
      |        PsiWhiteSpace(' ')
      |        AnnotationsList
      |          <empty list>
      |        Modifiers
      |          PsiElement(inline)('inline')
      |        PsiWhiteSpace(' ')
      |        PsiElement(def)('def')
      |        PsiWhiteSpace(' ')
      |        PsiElement(identifier)('b')
      |        Parameters
      |          <empty list>
      |        PsiWhiteSpace(' ')
      |        PsiElement(=)('=')
      |        PsiWhiteSpace(' ')
      |        IntegerLiteral
      |          PsiElement(integer)('35')
      |      PsiWhiteSpace('\n  ')
      |      MethodCall
      |        ReferenceExpression: println
      |          PsiElement(identifier)('println')
      |        ArgumentList
      |          PsiElement(()('(')
      |          InfixExpression
      |            ReferenceExpression: a
      |              PsiElement(identifier)('a')
      |            PsiWhiteSpace(' ')
      |            ReferenceExpression: +
      |              PsiElement(identifier)('+')
      |            PsiWhiteSpace(' ')
      |            ReferenceExpression: b
      |              PsiElement(identifier)('b')
      |          PsiElement())(')')
      |  PsiWhiteSpace('\n')""".stripMargin
  )

  def test_fn_block_starting_with_a_line_comment(): Unit = checkTree(
    """def foo =
      |  // some comment
      |  inline def a = 21
      |  // another comment
      |  inline def b = 35
      |  println(a + b)
      |""".stripMargin,
    """ScalaFile
      |  ScFunctionDefinition: foo
      |    AnnotationsList
      |      <empty list>
      |    Modifiers
      |      <empty list>
      |    PsiElement(def)('def')
      |    PsiWhiteSpace(' ')
      |    PsiElement(identifier)('foo')
      |    Parameters
      |      <empty list>
      |    PsiWhiteSpace(' ')
      |    PsiElement(=)('=')
      |    BlockExpression
      |      PsiWhiteSpace('\n  ')
      |      ScFunctionDefinition: a
      |        PsiComment(comment)('// some comment')
      |        PsiWhiteSpace('\n  ')
      |        AnnotationsList
      |          <empty list>
      |        Modifiers
      |          PsiElement(inline)('inline')
      |        PsiWhiteSpace(' ')
      |        PsiElement(def)('def')
      |        PsiWhiteSpace(' ')
      |        PsiElement(identifier)('a')
      |        Parameters
      |          <empty list>
      |        PsiWhiteSpace(' ')
      |        PsiElement(=)('=')
      |        PsiWhiteSpace(' ')
      |        IntegerLiteral
      |          PsiElement(integer)('21')
      |      PsiWhiteSpace('\n  ')
      |      ScFunctionDefinition: b
      |        PsiComment(comment)('// another comment')
      |        PsiWhiteSpace('\n  ')
      |        AnnotationsList
      |          <empty list>
      |        Modifiers
      |          PsiElement(inline)('inline')
      |        PsiWhiteSpace(' ')
      |        PsiElement(def)('def')
      |        PsiWhiteSpace(' ')
      |        PsiElement(identifier)('b')
      |        Parameters
      |          <empty list>
      |        PsiWhiteSpace(' ')
      |        PsiElement(=)('=')
      |        PsiWhiteSpace(' ')
      |        IntegerLiteral
      |          PsiElement(integer)('35')
      |      PsiWhiteSpace('\n  ')
      |      MethodCall
      |        ReferenceExpression: println
      |          PsiElement(identifier)('println')
      |        ArgumentList
      |          PsiElement(()('(')
      |          InfixExpression
      |            ReferenceExpression: a
      |              PsiElement(identifier)('a')
      |            PsiWhiteSpace(' ')
      |            ReferenceExpression: +
      |              PsiElement(identifier)('+')
      |            PsiWhiteSpace(' ')
      |            ReferenceExpression: b
      |              PsiElement(identifier)('b')
      |          PsiElement())(')')
      |  PsiWhiteSpace('\n')""".stripMargin
  )

  def test_fn_block_starting_with_a_block_comment(): Unit = checkTree(
    """def foo =
      |  /* some comment */
      |  inline def a = 21
      |  /* another comment */
      |  inline def b = 35
      |  println(a + b)
      |""".stripMargin,
    """ScalaFile
      |  ScFunctionDefinition: foo
      |    AnnotationsList
      |      <empty list>
      |    Modifiers
      |      <empty list>
      |    PsiElement(def)('def')
      |    PsiWhiteSpace(' ')
      |    PsiElement(identifier)('foo')
      |    Parameters
      |      <empty list>
      |    PsiWhiteSpace(' ')
      |    PsiElement(=)('=')
      |    BlockExpression
      |      PsiWhiteSpace('\n  ')
      |      ScFunctionDefinition: a
      |        PsiComment(BlockComment)('/* some comment */')
      |        PsiWhiteSpace('\n  ')
      |        AnnotationsList
      |          <empty list>
      |        Modifiers
      |          PsiElement(inline)('inline')
      |        PsiWhiteSpace(' ')
      |        PsiElement(def)('def')
      |        PsiWhiteSpace(' ')
      |        PsiElement(identifier)('a')
      |        Parameters
      |          <empty list>
      |        PsiWhiteSpace(' ')
      |        PsiElement(=)('=')
      |        PsiWhiteSpace(' ')
      |        IntegerLiteral
      |          PsiElement(integer)('21')
      |      PsiWhiteSpace('\n  ')
      |      ScFunctionDefinition: b
      |        PsiComment(BlockComment)('/* another comment */')
      |        PsiWhiteSpace('\n  ')
      |        AnnotationsList
      |          <empty list>
      |        Modifiers
      |          PsiElement(inline)('inline')
      |        PsiWhiteSpace(' ')
      |        PsiElement(def)('def')
      |        PsiWhiteSpace(' ')
      |        PsiElement(identifier)('b')
      |        Parameters
      |          <empty list>
      |        PsiWhiteSpace(' ')
      |        PsiElement(=)('=')
      |        PsiWhiteSpace(' ')
      |        IntegerLiteral
      |          PsiElement(integer)('35')
      |      PsiWhiteSpace('\n  ')
      |      MethodCall
      |        ReferenceExpression: println
      |          PsiElement(identifier)('println')
      |        ArgumentList
      |          PsiElement(()('(')
      |          InfixExpression
      |            ReferenceExpression: a
      |              PsiElement(identifier)('a')
      |            PsiWhiteSpace(' ')
      |            ReferenceExpression: +
      |              PsiElement(identifier)('+')
      |            PsiWhiteSpace(' ')
      |            ReferenceExpression: b
      |              PsiElement(identifier)('b')
      |          PsiElement())(')')
      |  PsiWhiteSpace('\n')""".stripMargin
  )

}
