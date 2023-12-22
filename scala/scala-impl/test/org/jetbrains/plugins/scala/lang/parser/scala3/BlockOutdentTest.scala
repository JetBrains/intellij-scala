package org.jetbrains.plugins.scala.lang.parser.scala3

class BlockOutdentTest extends SimpleScala3ParserTestBase {
  def test_result_expr_without_block(): Unit = checkTree(
    """
      |def test =
      |  () => a
      |   b
      |
      |def test2 =
      |  () => c
      |  d
      |
      |def test3 =
      |  () => e
      |f
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
      |    BlockExpression
      |      PsiWhiteSpace('\n  ')
      |      FunctionExpression
      |        Parameters
      |          ParametersClause
      |            PsiElement(()('(')
      |            PsiElement())(')')
      |        PsiWhiteSpace(' ')
      |        PsiElement(=>)('=>')
      |        PsiWhiteSpace(' ')
      |        ReferenceExpression: a
      |          PsiElement(identifier)('a')
      |      PsiWhiteSpace('\n   ')
      |      ReferenceExpression: b
      |        PsiElement(identifier)('b')
      |  PsiWhiteSpace('\n\n')
      |  ScFunctionDefinition: test2
      |    AnnotationsList
      |      <empty list>
      |    Modifiers
      |      <empty list>
      |    PsiElement(def)('def')
      |    PsiWhiteSpace(' ')
      |    PsiElement(identifier)('test2')
      |    Parameters
      |      <empty list>
      |    PsiWhiteSpace(' ')
      |    PsiElement(=)('=')
      |    BlockExpression
      |      PsiWhiteSpace('\n  ')
      |      FunctionExpression
      |        Parameters
      |          ParametersClause
      |            PsiElement(()('(')
      |            PsiElement())(')')
      |        PsiWhiteSpace(' ')
      |        PsiElement(=>)('=>')
      |        PsiWhiteSpace(' ')
      |        ReferenceExpression: c
      |          PsiElement(identifier)('c')
      |      PsiWhiteSpace('\n  ')
      |      ReferenceExpression: d
      |        PsiElement(identifier)('d')
      |  PsiWhiteSpace('\n\n')
      |  ScFunctionDefinition: test3
      |    AnnotationsList
      |      <empty list>
      |    Modifiers
      |      <empty list>
      |    PsiElement(def)('def')
      |    PsiWhiteSpace(' ')
      |    PsiElement(identifier)('test3')
      |    Parameters
      |      <empty list>
      |    PsiWhiteSpace(' ')
      |    PsiElement(=)('=')
      |    PsiWhiteSpace('\n  ')
      |    FunctionExpression
      |      Parameters
      |        ParametersClause
      |          PsiElement(()('(')
      |          PsiElement())(')')
      |      PsiWhiteSpace(' ')
      |      PsiElement(=>)('=>')
      |      PsiWhiteSpace(' ')
      |      ReferenceExpression: e
      |        PsiElement(identifier)('e')
      |  PsiWhiteSpace('\n')
      |  ReferenceExpression: f
      |    PsiElement(identifier)('f')
      |  PsiWhiteSpace('\n')
      |""".stripMargin
  )

  def test_result_expr_without_block_2(): Unit = checkTree(
    """
      |def test =
      |  --
      |  () => a
      |   b
      |
      |def test2 =
      |  --
      |  () => c
      |  d
      |
      |def test3 =
      |  --
      |  () => e
      |f
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
      |    BlockExpression
      |      PsiWhiteSpace('\n  ')
      |      ReferenceExpression: --
      |        PsiElement(identifier)('--')
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
      |          ReferenceExpression: a
      |            PsiElement(identifier)('a')
      |          PsiWhiteSpace('\n   ')
      |          ReferenceExpression: b
      |            PsiElement(identifier)('b')
      |  PsiWhiteSpace('\n\n')
      |  ScFunctionDefinition: test2
      |    AnnotationsList
      |      <empty list>
      |    Modifiers
      |      <empty list>
      |    PsiElement(def)('def')
      |    PsiWhiteSpace(' ')
      |    PsiElement(identifier)('test2')
      |    Parameters
      |      <empty list>
      |    PsiWhiteSpace(' ')
      |    PsiElement(=)('=')
      |    BlockExpression
      |      PsiWhiteSpace('\n  ')
      |      ReferenceExpression: --
      |        PsiElement(identifier)('--')
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
      |          ReferenceExpression: c
      |            PsiElement(identifier)('c')
      |          PsiWhiteSpace('\n  ')
      |          ReferenceExpression: d
      |            PsiElement(identifier)('d')
      |  PsiWhiteSpace('\n\n')
      |  ScFunctionDefinition: test3
      |    AnnotationsList
      |      <empty list>
      |    Modifiers
      |      <empty list>
      |    PsiElement(def)('def')
      |    PsiWhiteSpace(' ')
      |    PsiElement(identifier)('test3')
      |    Parameters
      |      <empty list>
      |    PsiWhiteSpace(' ')
      |    PsiElement(=)('=')
      |    BlockExpression
      |      PsiWhiteSpace('\n  ')
      |      ReferenceExpression: --
      |        PsiElement(identifier)('--')
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
      |          ReferenceExpression: e
      |            PsiElement(identifier)('e')
      |  PsiWhiteSpace('\n')
      |  ReferenceExpression: f
      |    PsiElement(identifier)('f')
      |  PsiWhiteSpace('\n')
      |""".stripMargin
  )

  def test_result_expr_with_block(): Unit = checkTree(
    """
      |def test = {
      |  () => a
      |   b
      |}
      |
      |def test2 = {
      |  () => c
      |  d
      |}
      |
      |def test3 = {
      |  () => e
      |f
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
      |      FunctionExpression
      |        Parameters
      |          ParametersClause
      |            PsiElement(()('(')
      |            PsiElement())(')')
      |        PsiWhiteSpace(' ')
      |        PsiElement(=>)('=>')
      |        PsiWhiteSpace(' ')
      |        BlockOfExpressions
      |          ReferenceExpression: a
      |            PsiElement(identifier)('a')
      |          PsiWhiteSpace('\n   ')
      |          ReferenceExpression: b
      |            PsiElement(identifier)('b')
      |      PsiWhiteSpace('\n')
      |      PsiElement(})('}')
      |  PsiWhiteSpace('\n\n')
      |  ScFunctionDefinition: test2
      |    AnnotationsList
      |      <empty list>
      |    Modifiers
      |      <empty list>
      |    PsiElement(def)('def')
      |    PsiWhiteSpace(' ')
      |    PsiElement(identifier)('test2')
      |    Parameters
      |      <empty list>
      |    PsiWhiteSpace(' ')
      |    PsiElement(=)('=')
      |    PsiWhiteSpace(' ')
      |    BlockExpression
      |      PsiElement({)('{')
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
      |          ReferenceExpression: c
      |            PsiElement(identifier)('c')
      |          PsiWhiteSpace('\n  ')
      |          ReferenceExpression: d
      |            PsiElement(identifier)('d')
      |      PsiWhiteSpace('\n')
      |      PsiElement(})('}')
      |  PsiWhiteSpace('\n\n')
      |  ScFunctionDefinition: test3
      |    AnnotationsList
      |      <empty list>
      |    Modifiers
      |      <empty list>
      |    PsiElement(def)('def')
      |    PsiWhiteSpace(' ')
      |    PsiElement(identifier)('test3')
      |    Parameters
      |      <empty list>
      |    PsiWhiteSpace(' ')
      |    PsiElement(=)('=')
      |    PsiWhiteSpace(' ')
      |    BlockExpression
      |      PsiElement({)('{')
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
      |          ReferenceExpression: e
      |            PsiElement(identifier)('e')
      |          PsiWhiteSpace('\n')
      |          ReferenceExpression: f
      |            PsiElement(identifier)('f')
      |      PsiWhiteSpace('\n')
      |      PsiElement(})('}')
      |  PsiWhiteSpace('\n')
      |""".stripMargin
  )

  def test_match_with_block(): Unit = checkTree(
    """
      |x match {
      |  case _ =>
      |    () => a
      |     b
      |}
      |
      |x match {
      |  case _ =>
      |    () => c
      |    d
      |}
      |
      |x match {
      |  case _ =>
      |    () => e
      |  f
      |}
      |""".stripMargin,
    """
      |ScalaFile
      |  PsiWhiteSpace('\n')
      |  MatchStatement
      |    ReferenceExpression: x
      |      PsiElement(identifier)('x')
      |    PsiWhiteSpace(' ')
      |    PsiElement(match)('match')
      |    PsiWhiteSpace(' ')
      |    PsiElement({)('{')
      |    PsiWhiteSpace('\n  ')
      |    CaseClauses
      |      CaseClause
      |        PsiElement(case)('case')
      |        PsiWhiteSpace(' ')
      |        WildcardPattern
      |          PsiElement(_)('_')
      |        PsiWhiteSpace(' ')
      |        PsiElement(=>)('=>')
      |        PsiWhiteSpace('\n    ')
      |        BlockOfExpressions
      |          FunctionExpression
      |            Parameters
      |              ParametersClause
      |                PsiElement(()('(')
      |                PsiElement())(')')
      |            PsiWhiteSpace(' ')
      |            PsiElement(=>)('=>')
      |            PsiWhiteSpace(' ')
      |            BlockOfExpressions
      |              ReferenceExpression: a
      |                PsiElement(identifier)('a')
      |              PsiWhiteSpace('\n     ')
      |              ReferenceExpression: b
      |                PsiElement(identifier)('b')
      |    PsiWhiteSpace('\n')
      |    PsiElement(})('}')
      |  PsiWhiteSpace('\n\n')
      |  MatchStatement
      |    ReferenceExpression: x
      |      PsiElement(identifier)('x')
      |    PsiWhiteSpace(' ')
      |    PsiElement(match)('match')
      |    PsiWhiteSpace(' ')
      |    PsiElement({)('{')
      |    PsiWhiteSpace('\n  ')
      |    CaseClauses
      |      CaseClause
      |        PsiElement(case)('case')
      |        PsiWhiteSpace(' ')
      |        WildcardPattern
      |          PsiElement(_)('_')
      |        PsiWhiteSpace(' ')
      |        PsiElement(=>)('=>')
      |        PsiWhiteSpace('\n    ')
      |        BlockOfExpressions
      |          FunctionExpression
      |            Parameters
      |              ParametersClause
      |                PsiElement(()('(')
      |                PsiElement())(')')
      |            PsiWhiteSpace(' ')
      |            PsiElement(=>)('=>')
      |            PsiWhiteSpace(' ')
      |            BlockOfExpressions
      |              ReferenceExpression: c
      |                PsiElement(identifier)('c')
      |              PsiWhiteSpace('\n    ')
      |              ReferenceExpression: d
      |                PsiElement(identifier)('d')
      |    PsiWhiteSpace('\n')
      |    PsiElement(})('}')
      |  PsiWhiteSpace('\n\n')
      |  MatchStatement
      |    ReferenceExpression: x
      |      PsiElement(identifier)('x')
      |    PsiWhiteSpace(' ')
      |    PsiElement(match)('match')
      |    PsiWhiteSpace(' ')
      |    PsiElement({)('{')
      |    PsiWhiteSpace('\n  ')
      |    CaseClauses
      |      CaseClause
      |        PsiElement(case)('case')
      |        PsiWhiteSpace(' ')
      |        WildcardPattern
      |          PsiElement(_)('_')
      |        PsiWhiteSpace(' ')
      |        PsiElement(=>)('=>')
      |        PsiWhiteSpace('\n    ')
      |        BlockOfExpressions
      |          FunctionExpression
      |            Parameters
      |              ParametersClause
      |                PsiElement(()('(')
      |                PsiElement())(')')
      |            PsiWhiteSpace(' ')
      |            PsiElement(=>)('=>')
      |            PsiWhiteSpace(' ')
      |            BlockOfExpressions
      |              ReferenceExpression: e
      |                PsiElement(identifier)('e')
      |              PsiWhiteSpace('\n  ')
      |              ReferenceExpression: f
      |                PsiElement(identifier)('f')
      |    PsiWhiteSpace('\n')
      |    PsiElement(})('}')
      |  PsiWhiteSpace('\n')
      |""".stripMargin
  )

  def test_semicolons(): Unit = checkTree(
    """
      |object O:
      |  xyz;
      |
      |  def foo1() =
      |    x; y;
      |  def foo2() =
      |    x; y; z;
      |    x; y; z;
      |""".stripMargin,
    """
      |ScalaFile
      |  PsiWhiteSpace('\n')
      |  ScObject: O
      |    AnnotationsList
      |      <empty list>
      |    Modifiers
      |      <empty list>
      |    PsiElement(object)('object')
      |    PsiWhiteSpace(' ')
      |    PsiElement(identifier)('O')
      |    ExtendsBlock
      |      ScTemplateBody
      |        PsiElement(:)(':')
      |        PsiWhiteSpace('\n  ')
      |        ReferenceExpression: xyz
      |          PsiElement(identifier)('xyz')
      |        PsiElement(;)(';')
      |        PsiWhiteSpace('\n\n  ')
      |        ScFunctionDefinition: foo1
      |          AnnotationsList
      |            <empty list>
      |          Modifiers
      |            <empty list>
      |          PsiElement(def)('def')
      |          PsiWhiteSpace(' ')
      |          PsiElement(identifier)('foo1')
      |          Parameters
      |            ParametersClause
      |              PsiElement(()('(')
      |              PsiElement())(')')
      |          PsiWhiteSpace(' ')
      |          PsiElement(=)('=')
      |          BlockExpression
      |            PsiWhiteSpace('\n    ')
      |            ReferenceExpression: x
      |              PsiElement(identifier)('x')
      |            PsiElement(;)(';')
      |            PsiWhiteSpace(' ')
      |            ReferenceExpression: y
      |              PsiElement(identifier)('y')
      |            PsiElement(;)(';')
      |        PsiWhiteSpace('\n  ')
      |        ScFunctionDefinition: foo2
      |          AnnotationsList
      |            <empty list>
      |          Modifiers
      |            <empty list>
      |          PsiElement(def)('def')
      |          PsiWhiteSpace(' ')
      |          PsiElement(identifier)('foo2')
      |          Parameters
      |            ParametersClause
      |              PsiElement(()('(')
      |              PsiElement())(')')
      |          PsiWhiteSpace(' ')
      |          PsiElement(=)('=')
      |          BlockExpression
      |            PsiWhiteSpace('\n    ')
      |            ReferenceExpression: x
      |              PsiElement(identifier)('x')
      |            PsiElement(;)(';')
      |            PsiWhiteSpace(' ')
      |            ReferenceExpression: y
      |              PsiElement(identifier)('y')
      |            PsiElement(;)(';')
      |            PsiWhiteSpace(' ')
      |            ReferenceExpression: z
      |              PsiElement(identifier)('z')
      |            PsiElement(;)(';')
      |            PsiWhiteSpace('\n    ')
      |            ReferenceExpression: x
      |              PsiElement(identifier)('x')
      |            PsiElement(;)(';')
      |            PsiWhiteSpace(' ')
      |            ReferenceExpression: y
      |              PsiElement(identifier)('y')
      |            PsiElement(;)(';')
      |            PsiWhiteSpace(' ')
      |            ReferenceExpression: z
      |              PsiElement(identifier)('z')
      |            PsiElement(;)(';')
      |  PsiWhiteSpace('\n')
      |""".stripMargin
  )

  def testLambdaWithInArgumentList_SingleLineLambda(): Unit = checkTree(
    """def foo(accu: Int) =
      |  Seq(
      |    (accu: Int) => 42,
      |    accu,
      |    42
      |  )
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
      |      ParametersClause
      |        PsiElement(()('(')
      |        Parameter: accu
      |          AnnotationsList
      |            <empty list>
      |          Modifiers
      |            <empty list>
      |          PsiElement(identifier)('accu')
      |          PsiElement(:)(':')
      |          PsiWhiteSpace(' ')
      |          ParameterType
      |            SimpleType: Int
      |              CodeReferenceElement: Int
      |                PsiElement(identifier)('Int')
      |        PsiElement())(')')
      |    PsiWhiteSpace(' ')
      |    PsiElement(=)('=')
      |    PsiWhiteSpace('\n  ')
      |    MethodCall
      |      ReferenceExpression: Seq
      |        PsiElement(identifier)('Seq')
      |      ArgumentList
      |        PsiElement(()('(')
      |        PsiWhiteSpace('\n    ')
      |        FunctionExpression
      |          Parameters
      |            ParametersClause
      |              PsiElement(()('(')
      |              Parameter: accu
      |                AnnotationsList
      |                  <empty list>
      |                PsiElement(identifier)('accu')
      |                PsiElement(:)(':')
      |                PsiWhiteSpace(' ')
      |                ParameterType
      |                  SimpleType: Int
      |                    CodeReferenceElement: Int
      |                      PsiElement(identifier)('Int')
      |              PsiElement())(')')
      |          PsiWhiteSpace(' ')
      |          PsiElement(=>)('=>')
      |          PsiWhiteSpace(' ')
      |          IntegerLiteral
      |            PsiElement(integer)('42')
      |        PsiElement(,)(',')
      |        PsiWhiteSpace('\n    ')
      |        ReferenceExpression: accu
      |          PsiElement(identifier)('accu')
      |        PsiElement(,)(',')
      |        PsiWhiteSpace('\n    ')
      |        IntegerLiteral
      |          PsiElement(integer)('42')
      |        PsiWhiteSpace('\n  ')
      |        PsiElement())(')')
      |  PsiWhiteSpace('\n')""".stripMargin
  )

  def testLambdaWithInArgumentList_SingleLineLambdaBodyOnNewLine(): Unit = checkTree(
    """def foo(accu: Int) =
      |  Seq(
      |    (accu: Int) =>
      |      42,
      |    accu,
      |    42
      |  )
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
      |      ParametersClause
      |        PsiElement(()('(')
      |        Parameter: accu
      |          AnnotationsList
      |            <empty list>
      |          Modifiers
      |            <empty list>
      |          PsiElement(identifier)('accu')
      |          PsiElement(:)(':')
      |          PsiWhiteSpace(' ')
      |          ParameterType
      |            SimpleType: Int
      |              CodeReferenceElement: Int
      |                PsiElement(identifier)('Int')
      |        PsiElement())(')')
      |    PsiWhiteSpace(' ')
      |    PsiElement(=)('=')
      |    PsiWhiteSpace('\n  ')
      |    MethodCall
      |      ReferenceExpression: Seq
      |        PsiElement(identifier)('Seq')
      |      ArgumentList
      |        PsiElement(()('(')
      |        PsiWhiteSpace('\n    ')
      |        FunctionExpression
      |          Parameters
      |            ParametersClause
      |              PsiElement(()('(')
      |              Parameter: accu
      |                AnnotationsList
      |                  <empty list>
      |                PsiElement(identifier)('accu')
      |                PsiElement(:)(':')
      |                PsiWhiteSpace(' ')
      |                ParameterType
      |                  SimpleType: Int
      |                    CodeReferenceElement: Int
      |                      PsiElement(identifier)('Int')
      |              PsiElement())(')')
      |          PsiWhiteSpace(' ')
      |          PsiElement(=>)('=>')
      |          PsiWhiteSpace('\n      ')
      |          IntegerLiteral
      |            PsiElement(integer)('42')
      |        PsiElement(,)(',')
      |        PsiWhiteSpace('\n    ')
      |        ReferenceExpression: accu
      |          PsiElement(identifier)('accu')
      |        PsiElement(,)(',')
      |        PsiWhiteSpace('\n    ')
      |        IntegerLiteral
      |          PsiElement(integer)('42')
      |        PsiWhiteSpace('\n  ')
      |        PsiElement())(')')
      |  PsiWhiteSpace('\n')""".stripMargin
  )

  def testLambdaWithInArgumentList_IndentationBasedLambdaBodyOnNewLine(): Unit = checkTree(
    """def foo(accu: Int) =
      |  Seq(
      |    (accu: Int) =>
      |      println(1)
      |      println(2)
      |      42,
      |    accu,
      |    42
      |  )
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
      |      ParametersClause
      |        PsiElement(()('(')
      |        Parameter: accu
      |          AnnotationsList
      |            <empty list>
      |          Modifiers
      |            <empty list>
      |          PsiElement(identifier)('accu')
      |          PsiElement(:)(':')
      |          PsiWhiteSpace(' ')
      |          ParameterType
      |            SimpleType: Int
      |              CodeReferenceElement: Int
      |                PsiElement(identifier)('Int')
      |        PsiElement())(')')
      |    PsiWhiteSpace(' ')
      |    PsiElement(=)('=')
      |    PsiWhiteSpace('\n  ')
      |    MethodCall
      |      ReferenceExpression: Seq
      |        PsiElement(identifier)('Seq')
      |      ArgumentList
      |        PsiElement(()('(')
      |        PsiWhiteSpace('\n    ')
      |        FunctionExpression
      |          Parameters
      |            ParametersClause
      |              PsiElement(()('(')
      |              Parameter: accu
      |                AnnotationsList
      |                  <empty list>
      |                PsiElement(identifier)('accu')
      |                PsiElement(:)(':')
      |                PsiWhiteSpace(' ')
      |                ParameterType
      |                  SimpleType: Int
      |                    CodeReferenceElement: Int
      |                      PsiElement(identifier)('Int')
      |              PsiElement())(')')
      |          PsiWhiteSpace(' ')
      |          PsiElement(=>)('=>')
      |          BlockExpression
      |            PsiWhiteSpace('\n      ')
      |            MethodCall
      |              ReferenceExpression: println
      |                PsiElement(identifier)('println')
      |              ArgumentList
      |                PsiElement(()('(')
      |                IntegerLiteral
      |                  PsiElement(integer)('1')
      |                PsiElement())(')')
      |            PsiWhiteSpace('\n      ')
      |            MethodCall
      |              ReferenceExpression: println
      |                PsiElement(identifier)('println')
      |              ArgumentList
      |                PsiElement(()('(')
      |                IntegerLiteral
      |                  PsiElement(integer)('2')
      |                PsiElement())(')')
      |            PsiWhiteSpace('\n      ')
      |            IntegerLiteral
      |              PsiElement(integer)('42')
      |        PsiElement(,)(',')
      |        PsiWhiteSpace('\n    ')
      |        ReferenceExpression: accu
      |          PsiElement(identifier)('accu')
      |        PsiElement(,)(',')
      |        PsiWhiteSpace('\n    ')
      |        IntegerLiteral
      |          PsiElement(integer)('42')
      |        PsiWhiteSpace('\n  ')
      |        PsiElement())(')')
      |  PsiWhiteSpace('\n')""".stripMargin
  )

  def testIndentationRegionInLambdaInsideParentheses(): Unit = checkTree(
    """List(1, 2, 3).map(x =>
      |  val y = x + 1
      |  y
      |)
      |""".stripMargin,
    """ScalaFile
      |  MethodCall
      |    ReferenceExpression: List(1, 2, 3).map
      |      MethodCall
      |        ReferenceExpression: List
      |          PsiElement(identifier)('List')
      |        ArgumentList
      |          PsiElement(()('(')
      |          IntegerLiteral
      |            PsiElement(integer)('1')
      |          PsiElement(,)(',')
      |          PsiWhiteSpace(' ')
      |          IntegerLiteral
      |            PsiElement(integer)('2')
      |          PsiElement(,)(',')
      |          PsiWhiteSpace(' ')
      |          IntegerLiteral
      |            PsiElement(integer)('3')
      |          PsiElement())(')')
      |      PsiElement(.)('.')
      |      PsiElement(identifier)('map')
      |    ArgumentList
      |      PsiElement(()('(')
      |      FunctionExpression
      |        Parameters
      |          ParametersClause
      |            Parameter: x
      |              PsiElement(identifier)('x')
      |        PsiWhiteSpace(' ')
      |        PsiElement(=>)('=>')
      |        BlockExpression
      |          PsiWhiteSpace('\n  ')
      |          ScPatternDefinition: y
      |            AnnotationsList
      |              <empty list>
      |            Modifiers
      |              <empty list>
      |            PsiElement(val)('val')
      |            PsiWhiteSpace(' ')
      |            ListOfPatterns
      |              ReferencePattern: y
      |                PsiElement(identifier)('y')
      |            PsiWhiteSpace(' ')
      |            PsiElement(=)('=')
      |            PsiWhiteSpace(' ')
      |            InfixExpression
      |              ReferenceExpression: x
      |                PsiElement(identifier)('x')
      |              PsiWhiteSpace(' ')
      |              ReferenceExpression: +
      |                PsiElement(identifier)('+')
      |              PsiWhiteSpace(' ')
      |              IntegerLiteral
      |                PsiElement(integer)('1')
      |          PsiWhiteSpace('\n  ')
      |          ReferenceExpression: y
      |            PsiElement(identifier)('y')
      |      PsiWhiteSpace('\n')
      |      PsiElement())(')')
      |  PsiWhiteSpace('\n')""".stripMargin
  )

  def testInfixExpressionInsideIndentedRegion(): Unit = checkTree(
    """Option(1).map(x =>
      |  if x == 0 then
      |    throw new IllegalArgumentException("wrong argument (" + x
      |      + ")")
      |  1.0 / x
      |)
      |""".stripMargin,
    """ScalaFile
      |  MethodCall
      |    ReferenceExpression: Option(1).map
      |      MethodCall
      |        ReferenceExpression: Option
      |          PsiElement(identifier)('Option')
      |        ArgumentList
      |          PsiElement(()('(')
      |          IntegerLiteral
      |            PsiElement(integer)('1')
      |          PsiElement())(')')
      |      PsiElement(.)('.')
      |      PsiElement(identifier)('map')
      |    ArgumentList
      |      PsiElement(()('(')
      |      FunctionExpression
      |        Parameters
      |          ParametersClause
      |            Parameter: x
      |              PsiElement(identifier)('x')
      |        PsiWhiteSpace(' ')
      |        PsiElement(=>)('=>')
      |        BlockExpression
      |          PsiWhiteSpace('\n  ')
      |          IfStatement
      |            PsiElement(if)('if')
      |            PsiWhiteSpace(' ')
      |            InfixExpression
      |              ReferenceExpression: x
      |                PsiElement(identifier)('x')
      |              PsiWhiteSpace(' ')
      |              ReferenceExpression: ==
      |                PsiElement(identifier)('==')
      |              PsiWhiteSpace(' ')
      |              IntegerLiteral
      |                PsiElement(integer)('0')
      |            PsiWhiteSpace(' ')
      |            PsiElement(then)('then')
      |            PsiWhiteSpace('\n    ')
      |            ThrowStatement
      |              PsiElement(throw)('throw')
      |              PsiWhiteSpace(' ')
      |              ScNewTemplateDefinition: <anonymous>
      |                PsiElement(new)('new')
      |                PsiWhiteSpace(' ')
      |                ExtendsBlock
      |                  TemplateParents
      |                    ConstructorInvocation
      |                      SimpleType: IllegalArgumentException
      |                        CodeReferenceElement: IllegalArgumentException
      |                          PsiElement(identifier)('IllegalArgumentException')
      |                      ArgumentList
      |                        PsiElement(()('(')
      |                        InfixExpression
      |                          InfixExpression
      |                            StringLiteral
      |                              PsiElement(string content)('"wrong argument ("')
      |                            PsiWhiteSpace(' ')
      |                            ReferenceExpression: +
      |                              PsiElement(identifier)('+')
      |                            PsiWhiteSpace(' ')
      |                            ReferenceExpression: x
      |                              PsiElement(identifier)('x')
      |                          PsiWhiteSpace('\n      ')
      |                          ReferenceExpression: +
      |                            PsiElement(identifier)('+')
      |                          PsiWhiteSpace(' ')
      |                          StringLiteral
      |                            PsiElement(string content)('")"')
      |                        PsiElement())(')')
      |          PsiWhiteSpace('\n  ')
      |          InfixExpression
      |            DoubleLiteral
      |              PsiElement(double)('1.0')
      |            PsiWhiteSpace(' ')
      |            ReferenceExpression: /
      |              PsiElement(identifier)('/')
      |            PsiWhiteSpace(' ')
      |            ReferenceExpression: x
      |              PsiElement(identifier)('x')
      |      PsiWhiteSpace('\n')
      |      PsiElement())(')')
      |  PsiWhiteSpace('\n')""".stripMargin
  )

  def testIncompleteArgBlockInCaseClause(): Unit = checkTree(
    """1 match
      |  case 1 =>
      |    func:
      |  case _ =>
      |""".stripMargin,
    """ScalaFile
      |  MatchStatement
      |    IntegerLiteral
      |      PsiElement(integer)('1')
      |    PsiWhiteSpace(' ')
      |    PsiElement(match)('match')
      |    PsiWhiteSpace('\n  ')
      |    CaseClauses
      |      CaseClause
      |        PsiElement(case)('case')
      |        PsiWhiteSpace(' ')
      |        LiteralPattern
      |          IntegerLiteral
      |            PsiElement(integer)('1')
      |        PsiWhiteSpace(' ')
      |        PsiElement(=>)('=>')
      |        PsiWhiteSpace('\n    ')
      |        BlockOfExpressions
      |          TypedExpression
      |            ReferenceExpression: func
      |              PsiElement(identifier)('func')
      |            PsiElement(:)(':')
      |            AnnotationsList
      |              <empty list>
      |            PsiErrorElement:Annotation or type expected
      |              <empty list>
      |      PsiWhiteSpace('\n  ')
      |      CaseClause
      |        PsiElement(case)('case')
      |        PsiWhiteSpace(' ')
      |        WildcardPattern
      |          PsiElement(_)('_')
      |        PsiWhiteSpace(' ')
      |        PsiElement(=>)('=>')
      |        BlockOfExpressions
      |          <empty list>
      |  PsiWhiteSpace('\n')
      |""".stripMargin
  )
}
