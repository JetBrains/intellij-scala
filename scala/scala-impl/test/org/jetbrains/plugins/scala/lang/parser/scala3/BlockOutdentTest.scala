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
}
