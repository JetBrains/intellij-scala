package org.jetbrains.plugins.scala.lang.parser.scala3

class ExprParserTest extends SimpleScala3ParserTestBase {

  def test_if_then_else(): Unit = checkTree(
    """
      |if a then b else c
      |""".stripMargin,
    """
      |ScalaFile
      |  PsiWhiteSpace('\n')
      |  IfStatement
      |    PsiElement(if)('if')
      |    PsiWhiteSpace(' ')
      |    ReferenceExpression: a
      |      PsiElement(identifier)('a')
      |    PsiWhiteSpace(' ')
      |    PsiElement(then)('then')
      |    PsiWhiteSpace(' ')
      |    ReferenceExpression: b
      |      PsiElement(identifier)('b')
      |    PsiWhiteSpace(' ')
      |    PsiElement(else)('else')
      |    PsiWhiteSpace(' ')
      |    ReferenceExpression: c
      |      PsiElement(identifier)('c')
      |  PsiWhiteSpace('\n')
      |""".stripMargin
  )

  def test_if_indented(): Unit = checkTree(
    """
      |if a then
      |  b
      |  c
      |else
      |  d
      |""".stripMargin,
    """
      |ScalaFile
      |  PsiWhiteSpace('\n')
      |  IfStatement
      |    PsiElement(if)('if')
      |    PsiWhiteSpace(' ')
      |    ReferenceExpression: a
      |      PsiElement(identifier)('a')
      |    PsiWhiteSpace(' ')
      |    PsiElement(then)('then')
      |    BlockExpression
      |      PsiWhiteSpace('\n  ')
      |      ReferenceExpression: b
      |        PsiElement(identifier)('b')
      |      PsiWhiteSpace('\n  ')
      |      ReferenceExpression: c
      |        PsiElement(identifier)('c')
      |    PsiWhiteSpace('\n')
      |    PsiElement(else)('else')
      |    PsiWhiteSpace('\n  ')
      |    ReferenceExpression: d
      |      PsiElement(identifier)('d')
      |  PsiWhiteSpace('\n')
      |""".stripMargin
  )

  def test_if_then_indented(): Unit = checkTree(
    """
      |if a then
      |  b
      |  c
      |else
      |  d
      |""".stripMargin,
    """
      |ScalaFile
      |  PsiWhiteSpace('\n')
      |  IfStatement
      |    PsiElement(if)('if')
      |    PsiWhiteSpace(' ')
      |    ReferenceExpression: a
      |      PsiElement(identifier)('a')
      |    PsiWhiteSpace(' ')
      |    PsiElement(then)('then')
      |    BlockExpression
      |      PsiWhiteSpace('\n  ')
      |      ReferenceExpression: b
      |        PsiElement(identifier)('b')
      |      PsiWhiteSpace('\n  ')
      |      ReferenceExpression: c
      |        PsiElement(identifier)('c')
      |    PsiWhiteSpace('\n')
      |    PsiElement(else)('else')
      |    PsiWhiteSpace('\n  ')
      |    ReferenceExpression: d
      |      PsiElement(identifier)('d')
      |  PsiWhiteSpace('\n')
      |""".stripMargin
  )

  def test_old_if(): Unit = checkTree(
    """
      |if (a) {
      |  b
      |} else {
      |  c
      |}
      |""".stripMargin,
    """
      |ScalaFile
      |  PsiWhiteSpace('\n')
      |  IfStatement
      |    PsiElement(if)('if')
      |    PsiWhiteSpace(' ')
      |    PsiElement(()('(')
      |    ReferenceExpression: a
      |      PsiElement(identifier)('a')
      |    PsiElement())(')')
      |    PsiWhiteSpace(' ')
      |    BlockExpression
      |      PsiElement({)('{')
      |      PsiWhiteSpace('\n  ')
      |      ReferenceExpression: b
      |        PsiElement(identifier)('b')
      |      PsiWhiteSpace('\n')
      |      PsiElement(})('}')
      |    PsiWhiteSpace(' ')
      |    PsiElement(else)('else')
      |    PsiWhiteSpace(' ')
      |    BlockExpression
      |      PsiElement({)('{')
      |      PsiWhiteSpace('\n  ')
      |      ReferenceExpression: c
      |        PsiElement(identifier)('c')
      |      PsiWhiteSpace('\n')
      |      PsiElement(})('}')
      |  PsiWhiteSpace('\n')
      |""".stripMargin
  )

  def test_old_if_inside_indentation_based_block(): Unit = checkTree(
    """def foo =
      |  1
      |  if (a) {
      |    println('a')
      |  } else if (b) {
      |    println('b')
      |  }
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
      |      IntegerLiteral
      |        PsiElement(integer)('1')
      |      PsiWhiteSpace('\n  ')
      |      IfStatement
      |        PsiElement(if)('if')
      |        PsiWhiteSpace(' ')
      |        PsiElement(()('(')
      |        ReferenceExpression: a
      |          PsiElement(identifier)('a')
      |        PsiElement())(')')
      |        PsiWhiteSpace(' ')
      |        BlockExpression
      |          PsiElement({)('{')
      |          PsiWhiteSpace('\n    ')
      |          MethodCall
      |            ReferenceExpression: println
      |              PsiElement(identifier)('println')
      |            ArgumentList
      |              PsiElement(()('(')
      |              CharLiteral
      |                PsiElement(Character)(''a'')
      |              PsiElement())(')')
      |          PsiWhiteSpace('\n  ')
      |          PsiElement(})('}')
      |        PsiWhiteSpace(' ')
      |        PsiElement(else)('else')
      |        PsiWhiteSpace(' ')
      |        IfStatement
      |          PsiElement(if)('if')
      |          PsiWhiteSpace(' ')
      |          PsiElement(()('(')
      |          ReferenceExpression: b
      |            PsiElement(identifier)('b')
      |          PsiElement())(')')
      |          PsiWhiteSpace(' ')
      |          BlockExpression
      |            PsiElement({)('{')
      |            PsiWhiteSpace('\n    ')
      |            MethodCall
      |              ReferenceExpression: println
      |                PsiElement(identifier)('println')
      |              ArgumentList
      |                PsiElement(()('(')
      |                CharLiteral
      |                  PsiElement(Character)(''b'')
      |                PsiElement())(')')
      |            PsiWhiteSpace('\n  ')
      |            PsiElement(})('}')
      |  PsiWhiteSpace('\n')""".stripMargin
  )

  def test_mixed_if_inside_braced_block_with_else_indented_to_the_left(): Unit = checkTree(
    """def foo = {
      |  if (a) {
      |    println('a')
      |  }
      |else if b then
      |  println('b')
      |}
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
      |    PsiWhiteSpace(' ')
      |    BlockExpression
      |      PsiElement({)('{')
      |      PsiWhiteSpace('\n  ')
      |      IfStatement
      |        PsiElement(if)('if')
      |        PsiWhiteSpace(' ')
      |        PsiElement(()('(')
      |        ReferenceExpression: a
      |          PsiElement(identifier)('a')
      |        PsiElement())(')')
      |        PsiWhiteSpace(' ')
      |        BlockExpression
      |          PsiElement({)('{')
      |          PsiWhiteSpace('\n    ')
      |          MethodCall
      |            ReferenceExpression: println
      |              PsiElement(identifier)('println')
      |            ArgumentList
      |              PsiElement(()('(')
      |              CharLiteral
      |                PsiElement(Character)(''a'')
      |              PsiElement())(')')
      |          PsiWhiteSpace('\n  ')
      |          PsiElement(})('}')
      |        PsiWhiteSpace('\n')
      |        PsiElement(else)('else')
      |        PsiWhiteSpace(' ')
      |        IfStatement
      |          PsiElement(if)('if')
      |          PsiWhiteSpace(' ')
      |          ReferenceExpression: b
      |            PsiElement(identifier)('b')
      |          PsiWhiteSpace(' ')
      |          PsiElement(then)('then')
      |          PsiWhiteSpace('\n  ')
      |          MethodCall
      |            ReferenceExpression: println
      |              PsiElement(identifier)('println')
      |            ArgumentList
      |              PsiElement(()('(')
      |              CharLiteral
      |                PsiElement(Character)(''b'')
      |              PsiElement())(')')
      |      PsiWhiteSpace('\n')
      |      PsiElement(})('}')
      |  PsiWhiteSpace('\n')""".stripMargin
  )

  def test_if_then_else_SCL_18769a(): Unit = checkTree(
    """
      |if a then
      |  b
      |  else
      |  c
      |""".stripMargin,
    """
      |ScalaFile
      |  PsiWhiteSpace('\n')
      |  IfStatement
      |    PsiElement(if)('if')
      |    PsiWhiteSpace(' ')
      |    ReferenceExpression: a
      |      PsiElement(identifier)('a')
      |    PsiWhiteSpace(' ')
      |    PsiElement(then)('then')
      |    PsiWhiteSpace('\n  ')
      |    ReferenceExpression: b
      |      PsiElement(identifier)('b')
      |    PsiWhiteSpace('\n  ')
      |    PsiElement(else)('else')
      |    PsiWhiteSpace('\n  ')
      |    ReferenceExpression: c
      |      PsiElement(identifier)('c')
      |  PsiWhiteSpace('\n')
      |""".stripMargin
  )

  def test_if_then_else_SCL_18769b(): Unit = checkTree(
    """
      |if a
      |then
      |  b
      |  c
      |  else
      |  d
      |""".stripMargin,
    """
      |ScalaFile
      |  PsiWhiteSpace('\n')
      |  IfStatement
      |    PsiElement(if)('if')
      |    PsiWhiteSpace(' ')
      |    ReferenceExpression: a
      |      PsiElement(identifier)('a')
      |    PsiWhiteSpace('\n')
      |    PsiElement(then)('then')
      |    BlockExpression
      |      PsiWhiteSpace('\n  ')
      |      ReferenceExpression: b
      |        PsiElement(identifier)('b')
      |      PsiWhiteSpace('\n  ')
      |      ReferenceExpression: c
      |        PsiElement(identifier)('c')
      |    PsiWhiteSpace('\n  ')
      |    PsiElement(else)('else')
      |    PsiWhiteSpace('\n  ')
      |    ReferenceExpression: d
      |      PsiElement(identifier)('d')
      |  PsiWhiteSpace('\n')
      |""".stripMargin
  )

  def test_new_line_in_if_condition(): Unit = checkTree(
    """
      |if a
      |  (x) then
      |  a
      |else
      |  b
      |""".stripMargin,
    """
      |ScalaFile
      |  PsiWhiteSpace('\n')
      |  IfStatement
      |    PsiElement(if)('if')
      |    PsiWhiteSpace(' ')
      |    MethodCall
      |      ReferenceExpression: a
      |        PsiElement(identifier)('a')
      |      PsiWhiteSpace('\n  ')
      |      ArgumentList
      |        PsiElement(()('(')
      |        ReferenceExpression: x
      |          PsiElement(identifier)('x')
      |        PsiElement())(')')
      |    PsiWhiteSpace(' ')
      |    PsiElement(then)('then')
      |    PsiWhiteSpace('\n  ')
      |    ReferenceExpression: a
      |      PsiElement(identifier)('a')
      |    PsiWhiteSpace('\n')
      |    PsiElement(else)('else')
      |    PsiWhiteSpace('\n  ')
      |    ReferenceExpression: b
      |      PsiElement(identifier)('b')
      |  PsiWhiteSpace('\n')
      |""".stripMargin
  )

  def test_if_then_else_inside_braced_block_with_else_indented_too_much_to_the_left(): Unit = checkTree(
    """def foo =
      |      if a then
      |        println('a')
      |  else if b then
      |    println('b')
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
      |    PsiWhiteSpace('\n      ')
      |    IfStatement
      |      PsiElement(if)('if')
      |      PsiWhiteSpace(' ')
      |      ReferenceExpression: a
      |        PsiElement(identifier)('a')
      |      PsiWhiteSpace(' ')
      |      PsiElement(then)('then')
      |      PsiWhiteSpace('\n        ')
      |      MethodCall
      |        ReferenceExpression: println
      |          PsiElement(identifier)('println')
      |        ArgumentList
      |          PsiElement(()('(')
      |          CharLiteral
      |            PsiElement(Character)(''a'')
      |          PsiElement())(')')
      |  PsiErrorElement:';' or newline expected
      |    <empty list>
      |  PsiWhiteSpace('\n  ')
      |  PsiElement(else)('else')
      |  PsiWhiteSpace(' ')
      |  IfStatement
      |    PsiElement(if)('if')
      |    PsiWhiteSpace(' ')
      |    ReferenceExpression: b
      |      PsiElement(identifier)('b')
      |    PsiWhiteSpace(' ')
      |    PsiElement(then)('then')
      |    PsiWhiteSpace('\n    ')
      |    MethodCall
      |      ReferenceExpression: println
      |        PsiElement(identifier)('println')
      |      ArgumentList
      |        PsiElement(()('(')
      |        CharLiteral
      |          PsiElement(Character)(''b'')
      |        PsiElement())(')')
      |  PsiWhiteSpace('\n')""".stripMargin
  )

  def test_nested_if_then_inside_if_then_else1(): Unit = checkTree(
    """if false then
      |  for case x <- 1 to 2 do
      |    if true then
      |      println('a')
      |else if true then
      |  for case x <- 1 to 2 do
      |    if true then
      |      println('b')
      |""".stripMargin,
    """ScalaFile
      |  IfStatement
      |    PsiElement(if)('if')
      |    PsiWhiteSpace(' ')
      |    BooleanLiteral
      |      PsiElement(false)('false')
      |    PsiWhiteSpace(' ')
      |    PsiElement(then)('then')
      |    PsiWhiteSpace('\n  ')
      |    ForStatement
      |      PsiElement(for)('for')
      |      PsiWhiteSpace(' ')
      |      Enumerators
      |        Generator
      |          PsiElement(case)('case')
      |          PsiWhiteSpace(' ')
      |          ReferencePattern: x
      |            PsiElement(identifier)('x')
      |          PsiWhiteSpace(' ')
      |          PsiElement(<-)('<-')
      |          PsiWhiteSpace(' ')
      |          InfixExpression
      |            IntegerLiteral
      |              PsiElement(integer)('1')
      |            PsiWhiteSpace(' ')
      |            ReferenceExpression: to
      |              PsiElement(identifier)('to')
      |            PsiWhiteSpace(' ')
      |            IntegerLiteral
      |              PsiElement(integer)('2')
      |      PsiWhiteSpace(' ')
      |      PsiElement(do)('do')
      |      PsiWhiteSpace('\n    ')
      |      IfStatement
      |        PsiElement(if)('if')
      |        PsiWhiteSpace(' ')
      |        BooleanLiteral
      |          PsiElement(true)('true')
      |        PsiWhiteSpace(' ')
      |        PsiElement(then)('then')
      |        PsiWhiteSpace('\n      ')
      |        MethodCall
      |          ReferenceExpression: println
      |            PsiElement(identifier)('println')
      |          ArgumentList
      |            PsiElement(()('(')
      |            CharLiteral
      |              PsiElement(Character)(''a'')
      |            PsiElement())(')')
      |    PsiWhiteSpace('\n')
      |    PsiElement(else)('else')
      |    PsiWhiteSpace(' ')
      |    IfStatement
      |      PsiElement(if)('if')
      |      PsiWhiteSpace(' ')
      |      BooleanLiteral
      |        PsiElement(true)('true')
      |      PsiWhiteSpace(' ')
      |      PsiElement(then)('then')
      |      PsiWhiteSpace('\n  ')
      |      ForStatement
      |        PsiElement(for)('for')
      |        PsiWhiteSpace(' ')
      |        Enumerators
      |          Generator
      |            PsiElement(case)('case')
      |            PsiWhiteSpace(' ')
      |            ReferencePattern: x
      |              PsiElement(identifier)('x')
      |            PsiWhiteSpace(' ')
      |            PsiElement(<-)('<-')
      |            PsiWhiteSpace(' ')
      |            InfixExpression
      |              IntegerLiteral
      |                PsiElement(integer)('1')
      |              PsiWhiteSpace(' ')
      |              ReferenceExpression: to
      |                PsiElement(identifier)('to')
      |              PsiWhiteSpace(' ')
      |              IntegerLiteral
      |                PsiElement(integer)('2')
      |        PsiWhiteSpace(' ')
      |        PsiElement(do)('do')
      |        PsiWhiteSpace('\n    ')
      |        IfStatement
      |          PsiElement(if)('if')
      |          PsiWhiteSpace(' ')
      |          BooleanLiteral
      |            PsiElement(true)('true')
      |          PsiWhiteSpace(' ')
      |          PsiElement(then)('then')
      |          PsiWhiteSpace('\n      ')
      |          MethodCall
      |            ReferenceExpression: println
      |              PsiElement(identifier)('println')
      |            ArgumentList
      |              PsiElement(()('(')
      |              CharLiteral
      |                PsiElement(Character)(''b'')
      |              PsiElement())(')')
      |  PsiWhiteSpace('\n')
      |""".stripMargin
  )

  def test_nested_if_then_inside_if_then_else2(): Unit = checkTree(
    """if false then
      |  for case x <- 1 to 2 do
      |    if true then
      |      println('a')
      |  else if true then
      |    for case x <- 1 to 2 do
      |      if true then
      |        println('b')
      |""".stripMargin,
    """ScalaFile
      |  IfStatement
      |    PsiElement(if)('if')
      |    PsiWhiteSpace(' ')
      |    BooleanLiteral
      |      PsiElement(false)('false')
      |    PsiWhiteSpace(' ')
      |    PsiElement(then)('then')
      |    PsiWhiteSpace('\n  ')
      |    ForStatement
      |      PsiElement(for)('for')
      |      PsiWhiteSpace(' ')
      |      Enumerators
      |        Generator
      |          PsiElement(case)('case')
      |          PsiWhiteSpace(' ')
      |          ReferencePattern: x
      |            PsiElement(identifier)('x')
      |          PsiWhiteSpace(' ')
      |          PsiElement(<-)('<-')
      |          PsiWhiteSpace(' ')
      |          InfixExpression
      |            IntegerLiteral
      |              PsiElement(integer)('1')
      |            PsiWhiteSpace(' ')
      |            ReferenceExpression: to
      |              PsiElement(identifier)('to')
      |            PsiWhiteSpace(' ')
      |            IntegerLiteral
      |              PsiElement(integer)('2')
      |      PsiWhiteSpace(' ')
      |      PsiElement(do)('do')
      |      PsiWhiteSpace('\n    ')
      |      IfStatement
      |        PsiElement(if)('if')
      |        PsiWhiteSpace(' ')
      |        BooleanLiteral
      |          PsiElement(true)('true')
      |        PsiWhiteSpace(' ')
      |        PsiElement(then)('then')
      |        PsiWhiteSpace('\n      ')
      |        MethodCall
      |          ReferenceExpression: println
      |            PsiElement(identifier)('println')
      |          ArgumentList
      |            PsiElement(()('(')
      |            CharLiteral
      |              PsiElement(Character)(''a'')
      |            PsiElement())(')')
      |    PsiWhiteSpace('\n  ')
      |    PsiElement(else)('else')
      |    PsiWhiteSpace(' ')
      |    IfStatement
      |      PsiElement(if)('if')
      |      PsiWhiteSpace(' ')
      |      BooleanLiteral
      |        PsiElement(true)('true')
      |      PsiWhiteSpace(' ')
      |      PsiElement(then)('then')
      |      PsiWhiteSpace('\n    ')
      |      ForStatement
      |        PsiElement(for)('for')
      |        PsiWhiteSpace(' ')
      |        Enumerators
      |          Generator
      |            PsiElement(case)('case')
      |            PsiWhiteSpace(' ')
      |            ReferencePattern: x
      |              PsiElement(identifier)('x')
      |            PsiWhiteSpace(' ')
      |            PsiElement(<-)('<-')
      |            PsiWhiteSpace(' ')
      |            InfixExpression
      |              IntegerLiteral
      |                PsiElement(integer)('1')
      |              PsiWhiteSpace(' ')
      |              ReferenceExpression: to
      |                PsiElement(identifier)('to')
      |              PsiWhiteSpace(' ')
      |              IntegerLiteral
      |                PsiElement(integer)('2')
      |        PsiWhiteSpace(' ')
      |        PsiElement(do)('do')
      |        PsiWhiteSpace('\n      ')
      |        IfStatement
      |          PsiElement(if)('if')
      |          PsiWhiteSpace(' ')
      |          BooleanLiteral
      |            PsiElement(true)('true')
      |          PsiWhiteSpace(' ')
      |          PsiElement(then)('then')
      |          PsiWhiteSpace('\n        ')
      |          MethodCall
      |            ReferenceExpression: println
      |              PsiElement(identifier)('println')
      |            ArgumentList
      |              PsiElement(()('(')
      |              CharLiteral
      |                PsiElement(Character)(''b'')
      |              PsiElement())(')')
      |  PsiWhiteSpace('\n')
      |""".stripMargin
  )

  def test_while_without_do_intended(): Unit = checkTree(
    """
      |while (a)
      |  b
      |  c
      |""".stripMargin,
    """
      |ScalaFile
      |  PsiWhiteSpace('\n')
      |  WhileStatement
      |    PsiElement(while)('while')
      |    PsiWhiteSpace(' ')
      |    PsiElement(()('(')
      |    ReferenceExpression: a
      |      PsiElement(identifier)('a')
      |    PsiElement())(')')
      |    BlockExpression
      |      PsiWhiteSpace('\n  ')
      |      ReferenceExpression: b
      |        PsiElement(identifier)('b')
      |      PsiWhiteSpace('\n  ')
      |      ReferenceExpression: c
      |        PsiElement(identifier)('c')
      |  PsiWhiteSpace('\n')
      |""".stripMargin
  )

  def test_while_do_intended(): Unit = checkTree(
    """
      |while
      |  a
      |  b
      |do
      |  c
      |  d
      |""".stripMargin,
    """
      |ScalaFile
      |  PsiWhiteSpace('\n')
      |  WhileStatement
      |    PsiElement(while)('while')
      |    BlockExpression
      |      PsiWhiteSpace('\n  ')
      |      ReferenceExpression: a
      |        PsiElement(identifier)('a')
      |      PsiWhiteSpace('\n  ')
      |      ReferenceExpression: b
      |        PsiElement(identifier)('b')
      |    PsiWhiteSpace('\n')
      |    PsiElement(do)('do')
      |    BlockExpression
      |      PsiWhiteSpace('\n  ')
      |      ReferenceExpression: c
      |        PsiElement(identifier)('c')
      |      PsiWhiteSpace('\n  ')
      |      ReferenceExpression: d
      |        PsiElement(identifier)('d')
      |  PsiWhiteSpace('\n')
      |""".stripMargin
  )

  def test_while_do_one_line(): Unit = checkTree(
    """
      |while a do b
      |""".stripMargin,
    """
      |ScalaFile
      |  PsiWhiteSpace('\n')
      |  WhileStatement
      |    PsiElement(while)('while')
      |    PsiWhiteSpace(' ')
      |    ReferenceExpression: a
      |      PsiElement(identifier)('a')
      |    PsiWhiteSpace(' ')
      |    PsiElement(do)('do')
      |    PsiWhiteSpace(' ')
      |    ReferenceExpression: b
      |      PsiElement(identifier)('b')
      |  PsiWhiteSpace('\n')
      |""".stripMargin
  )

  def test_while_without_do(): Unit = checkTree(
    """
      |while a
      |  b
      |""".stripMargin,
    """
      |ScalaFile
      |  PsiWhiteSpace('\n')
      |  WhileStatement
      |    PsiElement(while)('while')
      |    PsiWhiteSpace(' ')
      |    ReferenceExpression: a
      |      PsiElement(identifier)('a')
      |    PsiErrorElement:expected 'do'
      |      <empty list>
      |    PsiWhiteSpace('\n  ')
      |    ReferenceExpression: b
      |      PsiElement(identifier)('b')
      |  PsiWhiteSpace('\n')
      |""".stripMargin
  )

  def test_while_parenthesis_do(): Unit = checkTree(
    """
      |while (a) do b
      |""".stripMargin,
    """
      |ScalaFile
      |  PsiWhiteSpace('\n')
      |  WhileStatement
      |    PsiElement(while)('while')
      |    PsiWhiteSpace(' ')
      |    ExpressionInParenthesis
      |      PsiElement(()('(')
      |      ReferenceExpression: a
      |        PsiElement(identifier)('a')
      |      PsiElement())(')')
      |    PsiWhiteSpace(' ')
      |    PsiElement(do)('do')
      |    PsiWhiteSpace(' ')
      |    ReferenceExpression: b
      |      PsiElement(identifier)('b')
      |  PsiWhiteSpace('\n')
      |""".stripMargin
  )

  def test_old_while(): Unit = checkTree(
    """
      |while (a) {
      |  b
      |}
      |""".stripMargin,
    """
      |ScalaFile
      |  PsiWhiteSpace('\n')
      |  WhileStatement
      |    PsiElement(while)('while')
      |    PsiWhiteSpace(' ')
      |    PsiElement(()('(')
      |    ReferenceExpression: a
      |      PsiElement(identifier)('a')
      |    PsiElement())(')')
      |    PsiWhiteSpace(' ')
      |    BlockExpression
      |      PsiElement({)('{')
      |      PsiWhiteSpace('\n  ')
      |      ReferenceExpression: b
      |        PsiElement(identifier)('b')
      |      PsiWhiteSpace('\n')
      |      PsiElement(})('}')
      |  PsiWhiteSpace('\n')
      |""".stripMargin
  )

  def test_match_after_dot(): Unit = checkTree(
    """
      |x.y.match
      |  case _ => ???
      |""".stripMargin,
    """
      |ScalaFile
      |  PsiWhiteSpace('\n')
      |  MatchStatement
      |    ReferenceExpression: x.y
      |      ReferenceExpression: x
      |        PsiElement(identifier)('x')
      |      PsiElement(.)('.')
      |      PsiElement(identifier)('y')
      |    PsiElement(.)('.')
      |    PsiElement(match)('match')
      |    PsiWhiteSpace('\n  ')
      |    CaseClauses
      |      CaseClause
      |        PsiElement(case)('case')
      |        PsiWhiteSpace(' ')
      |        WildcardPattern
      |          PsiElement(_)('_')
      |        PsiWhiteSpace(' ')
      |        PsiElement(=>)('=>')
      |        PsiWhiteSpace(' ')
      |        BlockOfExpressions
      |          ReferenceExpression: ???
      |            PsiElement(identifier)('???')
      |  PsiWhiteSpace('\n')
      |""".stripMargin
  )

  def test_if_then_else_not_indented(): Unit = checkTree(
    """class A {
      |  if true then
      |  println(1)
      |  println(11)
      |
      |  if true then
      |  println(1)
      |  else
      |  println(2)
      |  println(22)
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
      |        IfStatement
      |          PsiElement(if)('if')
      |          PsiWhiteSpace(' ')
      |          BooleanLiteral
      |            PsiElement(true)('true')
      |          PsiWhiteSpace(' ')
      |          PsiElement(then)('then')
      |          PsiWhiteSpace('\n  ')
      |          MethodCall
      |            ReferenceExpression: println
      |              PsiElement(identifier)('println')
      |            ArgumentList
      |              PsiElement(()('(')
      |              IntegerLiteral
      |                PsiElement(integer)('1')
      |              PsiElement())(')')
      |        PsiWhiteSpace('\n  ')
      |        MethodCall
      |          ReferenceExpression: println
      |            PsiElement(identifier)('println')
      |          ArgumentList
      |            PsiElement(()('(')
      |            IntegerLiteral
      |              PsiElement(integer)('11')
      |            PsiElement())(')')
      |        PsiWhiteSpace('\n\n  ')
      |        IfStatement
      |          PsiElement(if)('if')
      |          PsiWhiteSpace(' ')
      |          BooleanLiteral
      |            PsiElement(true)('true')
      |          PsiWhiteSpace(' ')
      |          PsiElement(then)('then')
      |          PsiWhiteSpace('\n  ')
      |          MethodCall
      |            ReferenceExpression: println
      |              PsiElement(identifier)('println')
      |            ArgumentList
      |              PsiElement(()('(')
      |              IntegerLiteral
      |                PsiElement(integer)('1')
      |              PsiElement())(')')
      |          PsiWhiteSpace('\n  ')
      |          PsiElement(else)('else')
      |          PsiWhiteSpace('\n  ')
      |          MethodCall
      |            ReferenceExpression: println
      |              PsiElement(identifier)('println')
      |            ArgumentList
      |              PsiElement(()('(')
      |              IntegerLiteral
      |                PsiElement(integer)('2')
      |              PsiElement())(')')
      |        PsiWhiteSpace('\n  ')
      |        MethodCall
      |          ReferenceExpression: println
      |            PsiElement(identifier)('println')
      |          ArgumentList
      |            PsiElement(()('(')
      |            IntegerLiteral
      |              PsiElement(integer)('22')
      |            PsiElement())(')')
      |        PsiWhiteSpace('\n')
      |        PsiElement(})('}')""".stripMargin
  )
  
  def test_if_then_else_unindented(): Unit = checkTree(
    """class A {
      |  if true then
      | println(1)
      |  println(11)
      |
      |  if true then
      |  println(1)
      |  else
      |println(2)
      |  println(22)
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
      |        IfStatement
      |          PsiElement(if)('if')
      |          PsiWhiteSpace(' ')
      |          BooleanLiteral
      |            PsiElement(true)('true')
      |          PsiWhiteSpace(' ')
      |          PsiElement(then)('then')
      |          PsiErrorElement:Line is indented too far to the left
      |            <empty list>
      |          PsiWhiteSpace('\n ')
      |          MethodCall
      |            ReferenceExpression: println
      |              PsiElement(identifier)('println')
      |            ArgumentList
      |              PsiElement(()('(')
      |              IntegerLiteral
      |                PsiElement(integer)('1')
      |              PsiElement())(')')
      |        PsiWhiteSpace('\n  ')
      |        MethodCall
      |          ReferenceExpression: println
      |            PsiElement(identifier)('println')
      |          ArgumentList
      |            PsiElement(()('(')
      |            IntegerLiteral
      |              PsiElement(integer)('11')
      |            PsiElement())(')')
      |        PsiWhiteSpace('\n\n  ')
      |        IfStatement
      |          PsiElement(if)('if')
      |          PsiWhiteSpace(' ')
      |          BooleanLiteral
      |            PsiElement(true)('true')
      |          PsiWhiteSpace(' ')
      |          PsiElement(then)('then')
      |          PsiWhiteSpace('\n  ')
      |          MethodCall
      |            ReferenceExpression: println
      |              PsiElement(identifier)('println')
      |            ArgumentList
      |              PsiElement(()('(')
      |              IntegerLiteral
      |                PsiElement(integer)('1')
      |              PsiElement())(')')
      |          PsiWhiteSpace('\n  ')
      |          PsiElement(else)('else')
      |          PsiErrorElement:Line is indented too far to the left
      |            <empty list>
      |          PsiWhiteSpace('\n')
      |          MethodCall
      |            ReferenceExpression: println
      |              PsiElement(identifier)('println')
      |            ArgumentList
      |              PsiElement(()('(')
      |              IntegerLiteral
      |                PsiElement(integer)('2')
      |              PsiElement())(')')
      |        PsiWhiteSpace('\n  ')
      |        MethodCall
      |          ReferenceExpression: println
      |            PsiElement(identifier)('println')
      |          ArgumentList
      |            PsiElement(()('(')
      |            IntegerLiteral
      |              PsiElement(integer)('22')
      |            PsiElement())(')')
      |        PsiWhiteSpace('\n')
      |        PsiElement(})('}')""".stripMargin
  )

  def test_if_condition_is_block_without_braces(): Unit = checkTree(
      """if
        |  val x = 1
        |  val y = 2
        |  x + y == 3
        |then
        |  println("Yes!")
        |else
        |  println("No =(")
        |""".stripMargin,
    """ScalaFile
      |  IfStatement
      |    PsiElement(if)('if')
      |    BlockExpression
      |      PsiWhiteSpace('\n  ')
      |      ScPatternDefinition: x
      |        AnnotationsList
      |          <empty list>
      |        Modifiers
      |          <empty list>
      |        PsiElement(val)('val')
      |        PsiWhiteSpace(' ')
      |        ListOfPatterns
      |          ReferencePattern: x
      |            PsiElement(identifier)('x')
      |        PsiWhiteSpace(' ')
      |        PsiElement(=)('=')
      |        PsiWhiteSpace(' ')
      |        IntegerLiteral
      |          PsiElement(integer)('1')
      |      PsiWhiteSpace('\n  ')
      |      ScPatternDefinition: y
      |        AnnotationsList
      |          <empty list>
      |        Modifiers
      |          <empty list>
      |        PsiElement(val)('val')
      |        PsiWhiteSpace(' ')
      |        ListOfPatterns
      |          ReferencePattern: y
      |            PsiElement(identifier)('y')
      |        PsiWhiteSpace(' ')
      |        PsiElement(=)('=')
      |        PsiWhiteSpace(' ')
      |        IntegerLiteral
      |          PsiElement(integer)('2')
      |      PsiWhiteSpace('\n  ')
      |      InfixExpression
      |        InfixExpression
      |          ReferenceExpression: x
      |            PsiElement(identifier)('x')
      |          PsiWhiteSpace(' ')
      |          ReferenceExpression: +
      |            PsiElement(identifier)('+')
      |          PsiWhiteSpace(' ')
      |          ReferenceExpression: y
      |            PsiElement(identifier)('y')
      |        PsiWhiteSpace(' ')
      |        ReferenceExpression: ==
      |          PsiElement(identifier)('==')
      |        PsiWhiteSpace(' ')
      |        IntegerLiteral
      |          PsiElement(integer)('3')
      |    PsiWhiteSpace('\n')
      |    PsiElement(then)('then')
      |    PsiWhiteSpace('\n  ')
      |    MethodCall
      |      ReferenceExpression: println
      |        PsiElement(identifier)('println')
      |      ArgumentList
      |        PsiElement(()('(')
      |        StringLiteral
      |          PsiElement(string content)('"Yes!"')
      |        PsiElement())(')')
      |    PsiWhiteSpace('\n')
      |    PsiElement(else)('else')
      |    PsiWhiteSpace('\n  ')
      |    MethodCall
      |      ReferenceExpression: println
      |        PsiElement(identifier)('println')
      |      ArgumentList
      |        PsiElement(()('(')
      |        StringLiteral
      |          PsiElement(string content)('"No =("')
      |        PsiElement())(')')
      |  PsiWhiteSpace('\n')""".stripMargin
  )

  def test_if_condition_is_block_with_braces(): Unit = checkTree(
      """if {
        |  val x = 1
        |  val y = 2
        |  x + y == 3
        |}
        |then
        |  println("Yes!")
        |else
        |  println("No =(")
        |""".stripMargin,
    """ScalaFile
      |  IfStatement
      |    PsiElement(if)('if')
      |    PsiWhiteSpace(' ')
      |    BlockExpression
      |      PsiElement({)('{')
      |      PsiWhiteSpace('\n  ')
      |      ScPatternDefinition: x
      |        AnnotationsList
      |          <empty list>
      |        Modifiers
      |          <empty list>
      |        PsiElement(val)('val')
      |        PsiWhiteSpace(' ')
      |        ListOfPatterns
      |          ReferencePattern: x
      |            PsiElement(identifier)('x')
      |        PsiWhiteSpace(' ')
      |        PsiElement(=)('=')
      |        PsiWhiteSpace(' ')
      |        IntegerLiteral
      |          PsiElement(integer)('1')
      |      PsiWhiteSpace('\n  ')
      |      ScPatternDefinition: y
      |        AnnotationsList
      |          <empty list>
      |        Modifiers
      |          <empty list>
      |        PsiElement(val)('val')
      |        PsiWhiteSpace(' ')
      |        ListOfPatterns
      |          ReferencePattern: y
      |            PsiElement(identifier)('y')
      |        PsiWhiteSpace(' ')
      |        PsiElement(=)('=')
      |        PsiWhiteSpace(' ')
      |        IntegerLiteral
      |          PsiElement(integer)('2')
      |      PsiWhiteSpace('\n  ')
      |      InfixExpression
      |        InfixExpression
      |          ReferenceExpression: x
      |            PsiElement(identifier)('x')
      |          PsiWhiteSpace(' ')
      |          ReferenceExpression: +
      |            PsiElement(identifier)('+')
      |          PsiWhiteSpace(' ')
      |          ReferenceExpression: y
      |            PsiElement(identifier)('y')
      |        PsiWhiteSpace(' ')
      |        ReferenceExpression: ==
      |          PsiElement(identifier)('==')
      |        PsiWhiteSpace(' ')
      |        IntegerLiteral
      |          PsiElement(integer)('3')
      |      PsiWhiteSpace('\n')
      |      PsiElement(})('}')
      |    PsiWhiteSpace('\n')
      |    PsiElement(then)('then')
      |    PsiWhiteSpace('\n  ')
      |    MethodCall
      |      ReferenceExpression: println
      |        PsiElement(identifier)('println')
      |      ArgumentList
      |        PsiElement(()('(')
      |        StringLiteral
      |          PsiElement(string content)('"Yes!"')
      |        PsiElement())(')')
      |    PsiWhiteSpace('\n')
      |    PsiElement(else)('else')
      |    PsiWhiteSpace('\n  ')
      |    MethodCall
      |      ReferenceExpression: println
      |        PsiElement(identifier)('println')
      |      ArgumentList
      |        PsiElement(()('(')
      |        StringLiteral
      |          PsiElement(string content)('"No =("')
      |        PsiElement())(')')
      |  PsiWhiteSpace('\n')""".stripMargin
  )

  def test_while_condition_is_block_without_braces(): Unit = checkTree(
      """var idx = 2
        |while
        |  println("in while condition")
        |  idx -= 1
        |  idx >= 0
        |do
        |  println("in while body")
        |""".stripMargin,
    """ScalaFile
      |  ScVariableDefinition: idx
      |    AnnotationsList
      |      <empty list>
      |    Modifiers
      |      <empty list>
      |    PsiElement(var)('var')
      |    PsiWhiteSpace(' ')
      |    ListOfPatterns
      |      ReferencePattern: idx
      |        PsiElement(identifier)('idx')
      |    PsiWhiteSpace(' ')
      |    PsiElement(=)('=')
      |    PsiWhiteSpace(' ')
      |    IntegerLiteral
      |      PsiElement(integer)('2')
      |  PsiWhiteSpace('\n')
      |  WhileStatement
      |    PsiElement(while)('while')
      |    BlockExpression
      |      PsiWhiteSpace('\n  ')
      |      MethodCall
      |        ReferenceExpression: println
      |          PsiElement(identifier)('println')
      |        ArgumentList
      |          PsiElement(()('(')
      |          StringLiteral
      |            PsiElement(string content)('"in while condition"')
      |          PsiElement())(')')
      |      PsiWhiteSpace('\n  ')
      |      InfixExpression
      |        ReferenceExpression: idx
      |          PsiElement(identifier)('idx')
      |        PsiWhiteSpace(' ')
      |        ReferenceExpression: -=
      |          PsiElement(identifier)('-=')
      |        PsiWhiteSpace(' ')
      |        IntegerLiteral
      |          PsiElement(integer)('1')
      |      PsiWhiteSpace('\n  ')
      |      InfixExpression
      |        ReferenceExpression: idx
      |          PsiElement(identifier)('idx')
      |        PsiWhiteSpace(' ')
      |        ReferenceExpression: >=
      |          PsiElement(identifier)('>=')
      |        PsiWhiteSpace(' ')
      |        IntegerLiteral
      |          PsiElement(integer)('0')
      |    PsiWhiteSpace('\n')
      |    PsiElement(do)('do')
      |    PsiWhiteSpace('\n  ')
      |    MethodCall
      |      ReferenceExpression: println
      |        PsiElement(identifier)('println')
      |      ArgumentList
      |        PsiElement(()('(')
      |        StringLiteral
      |          PsiElement(string content)('"in while body"')
      |        PsiElement())(')')
      |  PsiWhiteSpace('\n')""".stripMargin
  )

  def test_while_condition_is_block_with_braces(): Unit = checkTree(
      """var idx = 2
        |while {
        |  println("in while condition")
        |  idx -= 1
        |  idx >= 0
        |}
        |do
        |  println("in while body")
        |""".stripMargin,
    """ScalaFile
      |  ScVariableDefinition: idx
      |    AnnotationsList
      |      <empty list>
      |    Modifiers
      |      <empty list>
      |    PsiElement(var)('var')
      |    PsiWhiteSpace(' ')
      |    ListOfPatterns
      |      ReferencePattern: idx
      |        PsiElement(identifier)('idx')
      |    PsiWhiteSpace(' ')
      |    PsiElement(=)('=')
      |    PsiWhiteSpace(' ')
      |    IntegerLiteral
      |      PsiElement(integer)('2')
      |  PsiWhiteSpace('\n')
      |  WhileStatement
      |    PsiElement(while)('while')
      |    PsiWhiteSpace(' ')
      |    BlockExpression
      |      PsiElement({)('{')
      |      PsiWhiteSpace('\n  ')
      |      MethodCall
      |        ReferenceExpression: println
      |          PsiElement(identifier)('println')
      |        ArgumentList
      |          PsiElement(()('(')
      |          StringLiteral
      |            PsiElement(string content)('"in while condition"')
      |          PsiElement())(')')
      |      PsiWhiteSpace('\n  ')
      |      InfixExpression
      |        ReferenceExpression: idx
      |          PsiElement(identifier)('idx')
      |        PsiWhiteSpace(' ')
      |        ReferenceExpression: -=
      |          PsiElement(identifier)('-=')
      |        PsiWhiteSpace(' ')
      |        IntegerLiteral
      |          PsiElement(integer)('1')
      |      PsiWhiteSpace('\n  ')
      |      InfixExpression
      |        ReferenceExpression: idx
      |          PsiElement(identifier)('idx')
      |        PsiWhiteSpace(' ')
      |        ReferenceExpression: >=
      |          PsiElement(identifier)('>=')
      |        PsiWhiteSpace(' ')
      |        IntegerLiteral
      |          PsiElement(integer)('0')
      |      PsiWhiteSpace('\n')
      |      PsiElement(})('}')
      |    PsiWhiteSpace('\n')
      |    PsiElement(do)('do')
      |    PsiWhiteSpace('\n  ')
      |    MethodCall
      |      ReferenceExpression: println
      |        PsiElement(identifier)('println')
      |      ArgumentList
      |        PsiElement(()('(')
      |        StringLiteral
      |          PsiElement(string content)('"in while body"')
      |        PsiElement())(')')
      |  PsiWhiteSpace('\n')""".stripMargin
  )

  def test_infix_after_comment(): Unit = checkTree(
    """
      |x
      |/*
      |*/ + 3
      |""".stripMargin,
    """
      |ScalaFile
      |  PsiWhiteSpace('\n')
      |  InfixExpression
      |    ReferenceExpression: x
      |      PsiElement(identifier)('x')
      |    PsiWhiteSpace('\n')
      |    PsiComment(BlockComment)('/*\n*/')
      |    PsiWhiteSpace(' ')
      |    ReferenceExpression: +
      |      PsiElement(identifier)('+')
      |    PsiWhiteSpace(' ')
      |    IntegerLiteral
      |      PsiElement(integer)('3')
      |  PsiWhiteSpace('\n')
      |""".stripMargin
  )

  def test_var_block_with_comment_after_assign(): Unit = checkTree(
    """var foo =   // Some comment
      |  val x = 2
      |  x + 2
      |""".stripMargin,
    """ScalaFile
      |  ScVariableDefinition: foo
      |    AnnotationsList
      |      <empty list>
      |    Modifiers
      |      <empty list>
      |    PsiElement(var)('var')
      |    PsiWhiteSpace(' ')
      |    ListOfPatterns
      |      ReferencePattern: foo
      |        PsiElement(identifier)('foo')
      |    PsiWhiteSpace(' ')
      |    PsiElement(=)('=')
      |    BlockExpression
      |      PsiWhiteSpace('   ')
      |      PsiComment(comment)('// Some comment')
      |      PsiWhiteSpace('\n  ')
      |      ScPatternDefinition: x
      |        AnnotationsList
      |          <empty list>
      |        Modifiers
      |          <empty list>
      |        PsiElement(val)('val')
      |        PsiWhiteSpace(' ')
      |        ListOfPatterns
      |          ReferencePattern: x
      |            PsiElement(identifier)('x')
      |        PsiWhiteSpace(' ')
      |        PsiElement(=)('=')
      |        PsiWhiteSpace(' ')
      |        IntegerLiteral
      |          PsiElement(integer)('2')
      |      PsiWhiteSpace('\n  ')
      |      InfixExpression
      |        ReferenceExpression: x
      |          PsiElement(identifier)('x')
      |        PsiWhiteSpace(' ')
      |        ReferenceExpression: +
      |          PsiElement(identifier)('+')
      |        PsiWhiteSpace(' ')
      |        IntegerLiteral
      |          PsiElement(integer)('2')
      |  PsiWhiteSpace('\n')""".stripMargin
  )

  def test_return(): Unit = checkTree(
    """1
      |return
      |2
      |return
      |  3
      |
      |if (true) return
      |4
      |if (true) return
      |  5
      |
      |if (true)
      |  return
      |  6
      |
      |if (true)
      |  return
      |    7
      |""".stripMargin,
    """ScalaFile
      |  IntegerLiteral
      |    PsiElement(integer)('1')
      |  PsiWhiteSpace('\n')
      |  ReturnStatement
      |    PsiElement(return)('return')
      |  PsiWhiteSpace('\n')
      |  IntegerLiteral
      |    PsiElement(integer)('2')
      |  PsiWhiteSpace('\n')
      |  ReturnStatement
      |    PsiElement(return)('return')
      |    PsiWhiteSpace('\n  ')
      |    IntegerLiteral
      |      PsiElement(integer)('3')
      |  PsiWhiteSpace('\n\n')
      |  IfStatement
      |    PsiElement(if)('if')
      |    PsiWhiteSpace(' ')
      |    PsiElement(()('(')
      |    BooleanLiteral
      |      PsiElement(true)('true')
      |    PsiElement())(')')
      |    PsiWhiteSpace(' ')
      |    ReturnStatement
      |      PsiElement(return)('return')
      |  PsiWhiteSpace('\n')
      |  IntegerLiteral
      |    PsiElement(integer)('4')
      |  PsiWhiteSpace('\n')
      |  IfStatement
      |    PsiElement(if)('if')
      |    PsiWhiteSpace(' ')
      |    PsiElement(()('(')
      |    BooleanLiteral
      |      PsiElement(true)('true')
      |    PsiElement())(')')
      |    PsiWhiteSpace(' ')
      |    ReturnStatement
      |      PsiElement(return)('return')
      |      PsiWhiteSpace('\n  ')
      |      IntegerLiteral
      |        PsiElement(integer)('5')
      |  PsiWhiteSpace('\n\n')
      |  IfStatement
      |    PsiElement(if)('if')
      |    PsiWhiteSpace(' ')
      |    PsiElement(()('(')
      |    BooleanLiteral
      |      PsiElement(true)('true')
      |    PsiElement())(')')
      |    BlockExpression
      |      PsiWhiteSpace('\n  ')
      |      ReturnStatement
      |        PsiElement(return)('return')
      |      PsiWhiteSpace('\n  ')
      |      IntegerLiteral
      |        PsiElement(integer)('6')
      |  PsiWhiteSpace('\n\n')
      |  IfStatement
      |    PsiElement(if)('if')
      |    PsiWhiteSpace(' ')
      |    PsiElement(()('(')
      |    BooleanLiteral
      |      PsiElement(true)('true')
      |    PsiElement())(')')
      |    PsiWhiteSpace('\n  ')
      |    ReturnStatement
      |      PsiElement(return)('return')
      |      PsiWhiteSpace('\n    ')
      |      IntegerLiteral
      |        PsiElement(integer)('7')
      |  PsiWhiteSpace('\n')
      |""".stripMargin
  )

  // SCL-21539
  def test_infix_expr_after_block_arg(): Unit = checkTree(
    """
      |// A("hi") :: Nil
      |return A:
      |  "hi"
      |:: Nil
      |
      |// "hi" :: Nil
      |return A:
      |  "hi"
      | :: Nil
      |""".stripMargin,
    """
      |ScalaFile
      |  PsiWhiteSpace('\n')
      |  PsiComment(comment)('// A("hi") :: Nil')
      |  PsiWhiteSpace('\n')
      |  ReturnStatement
      |    PsiElement(return)('return')
      |    PsiWhiteSpace(' ')
      |    InfixExpression
      |      MethodCall
      |        ReferenceExpression: A
      |          PsiElement(identifier)('A')
      |        ArgumentList
      |          BlockExpression
      |            PsiElement(:)(':')
      |            PsiWhiteSpace('\n  ')
      |            StringLiteral
      |              PsiElement(string content)('"hi"')
      |      PsiWhiteSpace('\n')
      |      ReferenceExpression: ::
      |        PsiElement(identifier)('::')
      |      PsiWhiteSpace(' ')
      |      ReferenceExpression: Nil
      |        PsiElement(identifier)('Nil')
      |  PsiWhiteSpace('\n\n')
      |  PsiComment(comment)('// "hi" :: Nil')
      |  PsiWhiteSpace('\n')
      |  ReturnStatement
      |    PsiElement(return)('return')
      |    PsiWhiteSpace(' ')
      |    MethodCall
      |      ReferenceExpression: A
      |        PsiElement(identifier)('A')
      |      ArgumentList
      |        BlockExpression
      |          PsiElement(:)(':')
      |          PsiWhiteSpace('\n  ')
      |          InfixExpression
      |            StringLiteral
      |              PsiElement(string content)('"hi"')
      |            PsiWhiteSpace('\n ')
      |            ReferenceExpression: ::
      |              PsiElement(identifier)('::')
      |            PsiWhiteSpace(' ')
      |            ReferenceExpression: Nil
      |              PsiElement(identifier)('Nil')
      |  PsiWhiteSpace('\n')
      |""".stripMargin
  )
}
