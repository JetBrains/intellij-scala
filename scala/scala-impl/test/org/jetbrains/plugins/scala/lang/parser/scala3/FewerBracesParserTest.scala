package org.jetbrains.plugins.scala.lang.parser.scala3

class FewerBracesParserTest extends SimpleScala3ParserTestBase {

  // ------------------------------------------------------
  //region Positive fewer braces parsing tests
  def test_fewer_braces(): Unit = checkTree(
    """val ys = xs.map: x =>
      |  x + 1
      |""".stripMargin,
    """
      |ScalaFile
      |  ScPatternDefinition: ys
      |    AnnotationsList
      |      <empty list>
      |    Modifiers
      |      <empty list>
      |    PsiElement(val)('val')
      |    PsiWhiteSpace(' ')
      |    ListOfPatterns
      |      ReferencePattern: ys
      |        PsiElement(identifier)('ys')
      |    PsiWhiteSpace(' ')
      |    PsiElement(=)('=')
      |    PsiWhiteSpace(' ')
      |    MethodCall
      |      ReferenceExpression: xs.map
      |        ReferenceExpression: xs
      |          PsiElement(identifier)('xs')
      |        PsiElement(.)('.')
      |        PsiElement(identifier)('map')
      |      ArgumentList
      |        BlockExpression
      |          PsiElement(:)(':')
      |          PsiWhiteSpace(' ')
      |          FunctionExpression
      |            Parameters
      |              ParametersClause
      |                Parameter: x
      |                  PsiElement(identifier)('x')
      |            PsiWhiteSpace(' ')
      |            PsiElement(=>)('=>')
      |            PsiWhiteSpace('\n  ')
      |            BlockOfExpressions
      |              InfixExpression
      |                ReferenceExpression: x
      |                  PsiElement(identifier)('x')
      |                PsiWhiteSpace(' ')
      |                ReferenceExpression: +
      |                  PsiElement(identifier)('+')
      |                PsiWhiteSpace(' ')
      |                IntegerLiteral
      |                  PsiElement(integer)('1')
      |  PsiWhiteSpace('\n')
      |""".stripMargin
  )

  def test_fewer_braces2(): Unit = checkTree(
    """val e = xs
      |  .filter: x =>
      |    x > 0
      |""".stripMargin,
    """
      |ScalaFile
      |  ScPatternDefinition: e
      |    AnnotationsList
      |      <empty list>
      |    Modifiers
      |      <empty list>
      |    PsiElement(val)('val')
      |    PsiWhiteSpace(' ')
      |    ListOfPatterns
      |      ReferencePattern: e
      |        PsiElement(identifier)('e')
      |    PsiWhiteSpace(' ')
      |    PsiElement(=)('=')
      |    PsiWhiteSpace(' ')
      |    MethodCall
      |      ReferenceExpression: xs
      |  .filter
      |        ReferenceExpression: xs
      |          PsiElement(identifier)('xs')
      |        PsiWhiteSpace('\n  ')
      |        PsiElement(.)('.')
      |        PsiElement(identifier)('filter')
      |      ArgumentList
      |        BlockExpression
      |          PsiElement(:)(':')
      |          PsiWhiteSpace(' ')
      |          FunctionExpression
      |            Parameters
      |              ParametersClause
      |                Parameter: x
      |                  PsiElement(identifier)('x')
      |            PsiWhiteSpace(' ')
      |            PsiElement(=>)('=>')
      |            PsiWhiteSpace('\n    ')
      |            BlockOfExpressions
      |              InfixExpression
      |                ReferenceExpression: x
      |                  PsiElement(identifier)('x')
      |                PsiWhiteSpace(' ')
      |                ReferenceExpression: >
      |                  PsiElement(identifier)('>')
      |                PsiWhiteSpace(' ')
      |                IntegerLiteral
      |                  PsiElement(integer)('0')
      |  PsiWhiteSpace('\n')
      |""".stripMargin
  )

  def test_fewer_braces_argument_on_new_line(): Unit = checkTree(
    """val e = xs
      |.filter:
      |  someFn
      |""".stripMargin,
    """
      |ScalaFile
      |  ScPatternDefinition: e
      |    AnnotationsList
      |      <empty list>
      |    Modifiers
      |      <empty list>
      |    PsiElement(val)('val')
      |    PsiWhiteSpace(' ')
      |    ListOfPatterns
      |      ReferencePattern: e
      |        PsiElement(identifier)('e')
      |    PsiWhiteSpace(' ')
      |    PsiElement(=)('=')
      |    PsiWhiteSpace(' ')
      |    MethodCall
      |      ReferenceExpression: xs
      |.filter
      |        ReferenceExpression: xs
      |          PsiElement(identifier)('xs')
      |        PsiWhiteSpace('\n')
      |        PsiElement(.)('.')
      |        PsiElement(identifier)('filter')
      |      ArgumentList
      |        BlockExpression
      |          PsiElement(:)(':')
      |          PsiWhiteSpace('\n  ')
      |          ReferenceExpression: someFn
      |            PsiElement(identifier)('someFn')
      |  PsiWhiteSpace('\n')
      |""".stripMargin
  )

  def test_fewer_braces_implicit_on_new_line(): Unit = checkTree(
    """val e = xs
      |  .filter:
      |    implicit x => x > 0
      |""".stripMargin,
    """
      |ScalaFile
      |  ScPatternDefinition: e
      |    AnnotationsList
      |      <empty list>
      |    Modifiers
      |      <empty list>
      |    PsiElement(val)('val')
      |    PsiWhiteSpace(' ')
      |    ListOfPatterns
      |      ReferencePattern: e
      |        PsiElement(identifier)('e')
      |    PsiWhiteSpace(' ')
      |    PsiElement(=)('=')
      |    PsiWhiteSpace(' ')
      |    MethodCall
      |      ReferenceExpression: xs
      |  .filter
      |        ReferenceExpression: xs
      |          PsiElement(identifier)('xs')
      |        PsiWhiteSpace('\n  ')
      |        PsiElement(.)('.')
      |        PsiElement(identifier)('filter')
      |      ArgumentList
      |        BlockExpression
      |          PsiElement(:)(':')
      |          PsiWhiteSpace('\n    ')
      |          FunctionExpression
      |            Parameters
      |              ParametersClause
      |                PsiElement(implicit)('implicit')
      |                PsiWhiteSpace(' ')
      |                Parameter: x
      |                  PsiElement(identifier)('x')
      |            PsiWhiteSpace(' ')
      |            PsiElement(=>)('=>')
      |            PsiWhiteSpace(' ')
      |            BlockOfExpressions
      |              InfixExpression
      |                ReferenceExpression: x
      |                  PsiElement(identifier)('x')
      |                PsiWhiteSpace(' ')
      |                ReferenceExpression: >
      |                  PsiElement(identifier)('>')
      |                PsiWhiteSpace(' ')
      |                IntegerLiteral
      |                  PsiElement(integer)('0')
      |  PsiWhiteSpace('\n')
      |""".stripMargin
  )

  def test_fewer_braces_infix(): Unit = checkTree(
    """List(1) map: x =>
      |    x + 1
      |""".stripMargin,
    """
      |ScalaFile
      |  InfixExpression
      |    MethodCall
      |      ReferenceExpression: List
      |        PsiElement(identifier)('List')
      |      ArgumentList
      |        PsiElement(()('(')
      |        IntegerLiteral
      |          PsiElement(integer)('1')
      |        PsiElement())(')')
      |    PsiWhiteSpace(' ')
      |    ReferenceExpression: map
      |      PsiElement(identifier)('map')
      |    BlockExpression
      |      PsiElement(:)(':')
      |      PsiWhiteSpace(' ')
      |      FunctionExpression
      |        Parameters
      |          ParametersClause
      |            Parameter: x
      |              PsiElement(identifier)('x')
      |        PsiWhiteSpace(' ')
      |        PsiElement(=>)('=>')
      |        PsiWhiteSpace('\n    ')
      |        BlockOfExpressions
      |          InfixExpression
      |            ReferenceExpression: x
      |              PsiElement(identifier)('x')
      |            PsiWhiteSpace(' ')
      |            ReferenceExpression: +
      |              PsiElement(identifier)('+')
      |            PsiWhiteSpace(' ')
      |            IntegerLiteral
      |              PsiElement(integer)('1')
      |  PsiWhiteSpace('\n')
      |""".stripMargin
  )

  def test_fewer_braces_bindings(): Unit = checkTree(
    """val x = ys.foldLeft(0): (x, y) =>
      |  x + y
      |""".stripMargin,
    """
      |ScalaFile
      |  ScPatternDefinition: x
      |    AnnotationsList
      |      <empty list>
      |    Modifiers
      |      <empty list>
      |    PsiElement(val)('val')
      |    PsiWhiteSpace(' ')
      |    ListOfPatterns
      |      ReferencePattern: x
      |        PsiElement(identifier)('x')
      |    PsiWhiteSpace(' ')
      |    PsiElement(=)('=')
      |    PsiWhiteSpace(' ')
      |    MethodCall
      |      MethodCall
      |        ReferenceExpression: ys.foldLeft
      |          ReferenceExpression: ys
      |            PsiElement(identifier)('ys')
      |          PsiElement(.)('.')
      |          PsiElement(identifier)('foldLeft')
      |        ArgumentList
      |          PsiElement(()('(')
      |          IntegerLiteral
      |            PsiElement(integer)('0')
      |          PsiElement())(')')
      |      ArgumentList
      |        BlockExpression
      |          PsiElement(:)(':')
      |          PsiWhiteSpace(' ')
      |          FunctionExpression
      |            Parameters
      |              ParametersClause
      |                PsiElement(()('(')
      |                Parameter: x
      |                  AnnotationsList
      |                    <empty list>
      |                  PsiElement(identifier)('x')
      |                PsiElement(,)(',')
      |                PsiWhiteSpace(' ')
      |                Parameter: y
      |                  AnnotationsList
      |                    <empty list>
      |                  PsiElement(identifier)('y')
      |                PsiElement())(')')
      |            PsiWhiteSpace(' ')
      |            PsiElement(=>)('=>')
      |            PsiWhiteSpace('\n  ')
      |            BlockOfExpressions
      |              InfixExpression
      |                ReferenceExpression: x
      |                  PsiElement(identifier)('x')
      |                PsiWhiteSpace(' ')
      |                ReferenceExpression: +
      |                  PsiElement(identifier)('+')
      |                PsiWhiteSpace(' ')
      |                ReferenceExpression: y
      |                  PsiElement(identifier)('y')
      |  PsiWhiteSpace('\n')
      |""".stripMargin
  )

  def test_fewer_braces_bindings_with_types(): Unit = checkTree(
    """val y = ys.foldLeft(0): (x: Int, y: Int) =>
      |  val z = x + y
      |  z * z
      |""".stripMargin,
    """
      |ScalaFile
      |  ScPatternDefinition: y
      |    AnnotationsList
      |      <empty list>
      |    Modifiers
      |      <empty list>
      |    PsiElement(val)('val')
      |    PsiWhiteSpace(' ')
      |    ListOfPatterns
      |      ReferencePattern: y
      |        PsiElement(identifier)('y')
      |    PsiWhiteSpace(' ')
      |    PsiElement(=)('=')
      |    PsiWhiteSpace(' ')
      |    MethodCall
      |      MethodCall
      |        ReferenceExpression: ys.foldLeft
      |          ReferenceExpression: ys
      |            PsiElement(identifier)('ys')
      |          PsiElement(.)('.')
      |          PsiElement(identifier)('foldLeft')
      |        ArgumentList
      |          PsiElement(()('(')
      |          IntegerLiteral
      |            PsiElement(integer)('0')
      |          PsiElement())(')')
      |      ArgumentList
      |        BlockExpression
      |          PsiElement(:)(':')
      |          PsiWhiteSpace(' ')
      |          FunctionExpression
      |            Parameters
      |              ParametersClause
      |                PsiElement(()('(')
      |                Parameter: x
      |                  AnnotationsList
      |                    <empty list>
      |                  PsiElement(identifier)('x')
      |                  PsiElement(:)(':')
      |                  PsiWhiteSpace(' ')
      |                  ParameterType
      |                    SimpleType: Int
      |                      CodeReferenceElement: Int
      |                        PsiElement(identifier)('Int')
      |                PsiElement(,)(',')
      |                PsiWhiteSpace(' ')
      |                Parameter: y
      |                  AnnotationsList
      |                    <empty list>
      |                  PsiElement(identifier)('y')
      |                  PsiElement(:)(':')
      |                  PsiWhiteSpace(' ')
      |                  ParameterType
      |                    SimpleType: Int
      |                      CodeReferenceElement: Int
      |                        PsiElement(identifier)('Int')
      |                PsiElement())(')')
      |            PsiWhiteSpace(' ')
      |            PsiElement(=>)('=>')
      |            PsiWhiteSpace('\n  ')
      |            BlockOfExpressions
      |              ScPatternDefinition: z
      |                AnnotationsList
      |                  <empty list>
      |                Modifiers
      |                  <empty list>
      |                PsiElement(val)('val')
      |                PsiWhiteSpace(' ')
      |                ListOfPatterns
      |                  ReferencePattern: z
      |                    PsiElement(identifier)('z')
      |                PsiWhiteSpace(' ')
      |                PsiElement(=)('=')
      |                PsiWhiteSpace(' ')
      |                InfixExpression
      |                  ReferenceExpression: x
      |                    PsiElement(identifier)('x')
      |                  PsiWhiteSpace(' ')
      |                  ReferenceExpression: +
      |                    PsiElement(identifier)('+')
      |                  PsiWhiteSpace(' ')
      |                  ReferenceExpression: y
      |                    PsiElement(identifier)('y')
      |              PsiWhiteSpace('\n  ')
      |              InfixExpression
      |                ReferenceExpression: z
      |                  PsiElement(identifier)('z')
      |                PsiWhiteSpace(' ')
      |                ReferenceExpression: *
      |                  PsiElement(identifier)('*')
      |                PsiWhiteSpace(' ')
      |                ReferenceExpression: z
      |                  PsiElement(identifier)('z')
      |  PsiWhiteSpace('\n')
      |""".stripMargin
  )

  // TODO: SCL-21085
  def _test_fewer_braces_simple_arg_in_parens(): Unit = checkTree(
    """val a = xs
      |  (0)
      |""".stripMargin,
    """
      |ScalaFile
      |  ScPatternDefinition: a
      |    AnnotationsList
      |      <empty list>
      |    Modifiers
      |      <empty list>
      |    PsiElement(val)('val')
      |    PsiWhiteSpace(' ')
      |    ListOfPatterns
      |      ReferencePattern: a
      |        PsiElement(identifier)('a')
      |    PsiWhiteSpace(' ')
      |    PsiElement(=)('=')
      |    PsiWhiteSpace(' ')
      |    MethodCall
      |      ReferenceExpression: xs
      |        PsiElement(identifier)('xs')
      |      PsiWhiteSpace('\n  ')
      |      ArgumentList
      |        PsiElement(()('(')
      |        IntegerLiteral
      |          PsiElement(integer)('0')
      |        PsiElement())(')')
      |  PsiWhiteSpace('\n')
      |""".stripMargin
  )

  // TODO: SCL-21085
  def _test_fewer_braces_simple_arg_in_parens2(): Unit = checkTree(
    """val a =
      |  xs
      |   (0)
      |""".stripMargin,
    """
      |ScalaFile
      |  ScPatternDefinition: a
      |    AnnotationsList
      |      <empty list>
      |    Modifiers
      |      <empty list>
      |    PsiElement(val)('val')
      |    PsiWhiteSpace(' ')
      |    ListOfPatterns
      |      ReferencePattern: a
      |        PsiElement(identifier)('a')
      |    PsiWhiteSpace(' ')
      |    PsiElement(=)('=')
      |    PsiWhiteSpace('\n  ')
      |    MethodCall
      |      ReferenceExpression: xs
      |        PsiElement(identifier)('xs')
      |      PsiWhiteSpace('\n   ')
      |      ArgumentList
      |        PsiElement(()('(')
      |        IntegerLiteral
      |          PsiElement(integer)('0')
      |        PsiElement())(')')
      |  PsiWhiteSpace('\n')
      |""".stripMargin
  )

  // TODO: SCL-21085
  def _test_fewer_braces_multiple_calls_and_args_in_parens(): Unit = checkTree(
    """val a: Int = xs
      |  .map: x =>
      |    x * x
      |  .filter: (y: Int) =>
      |    y > 0
      |  (0)
      |""".stripMargin,
    """
      |ScalaFile
      |  ScPatternDefinition: a
      |    AnnotationsList
      |      <empty list>
      |    Modifiers
      |      <empty list>
      |    PsiElement(val)('val')
      |    PsiWhiteSpace(' ')
      |    ListOfPatterns
      |      ReferencePattern: a
      |        PsiElement(identifier)('a')
      |    PsiElement(:)(':')
      |    PsiWhiteSpace(' ')
      |    SimpleType: Int
      |      CodeReferenceElement: Int
      |        PsiElement(identifier)('Int')
      |    PsiWhiteSpace(' ')
      |    PsiElement(=)('=')
      |    PsiWhiteSpace(' ')
      |    MethodCall
      |      MethodCall
      |        ReferenceExpression: xs
      |  .map: x =>
      |    x * x
      |  .filter
      |          MethodCall
      |            ReferenceExpression: xs
      |  .map
      |              ReferenceExpression: xs
      |                PsiElement(identifier)('xs')
      |              PsiWhiteSpace('\n  ')
      |              PsiElement(.)('.')
      |              PsiElement(identifier)('map')
      |            ArgumentList
      |              BlockExpression
      |                PsiElement(:)(':')
      |                PsiWhiteSpace(' ')
      |                FunctionExpression
      |                  Parameters
      |                    ParametersClause
      |                      Parameter: x
      |                        PsiElement(identifier)('x')
      |                  PsiWhiteSpace(' ')
      |                  PsiElement(=>)('=>')
      |                  PsiWhiteSpace('\n    ')
      |                  BlockOfExpressions
      |                    InfixExpression
      |                      ReferenceExpression: x
      |                        PsiElement(identifier)('x')
      |                      PsiWhiteSpace(' ')
      |                      ReferenceExpression: *
      |                        PsiElement(identifier)('*')
      |                      PsiWhiteSpace(' ')
      |                      ReferenceExpression: x
      |                        PsiElement(identifier)('x')
      |          PsiWhiteSpace('\n  ')
      |          PsiElement(.)('.')
      |          PsiElement(identifier)('filter')
      |        ArgumentList
      |          BlockExpression
      |            PsiElement(:)(':')
      |            PsiWhiteSpace(' ')
      |            FunctionExpression
      |              Parameters
      |                ParametersClause
      |                  PsiElement(()('(')
      |                  Parameter: y
      |                    AnnotationsList
      |                      <empty list>
      |                    PsiElement(identifier)('y')
      |                    PsiElement(:)(':')
      |                    PsiWhiteSpace(' ')
      |                    ParameterType
      |                      SimpleType: Int
      |                        CodeReferenceElement: Int
      |                          PsiElement(identifier)('Int')
      |                  PsiElement())(')')
      |              PsiWhiteSpace(' ')
      |              PsiElement(=>)('=>')
      |              PsiWhiteSpace('\n    ')
      |              BlockOfExpressions
      |                InfixExpression
      |                  ReferenceExpression: y
      |                    PsiElement(identifier)('y')
      |                  PsiWhiteSpace(' ')
      |                  ReferenceExpression: >
      |                    PsiElement(identifier)('>')
      |                  PsiWhiteSpace(' ')
      |                  IntegerLiteral
      |                    PsiElement(integer)('0')
      |      PsiWhiteSpace('\n  ')
      |      ArgumentList
      |        PsiElement(()('(')
      |        IntegerLiteral
      |          PsiElement(integer)('0')
      |        PsiElement())(')')
      |  PsiWhiteSpace('\n')
      |""".stripMargin
  )

  def test_fewer_braces_case_clauses(): Unit = checkTree(
    """val e = xs.map:
      |    case 1 => 2
      |    case x => x
      |  .filter: x =>
      |    x > 0
      |""".stripMargin,
    """
      |ScalaFile
      |  ScPatternDefinition: e
      |    AnnotationsList
      |      <empty list>
      |    Modifiers
      |      <empty list>
      |    PsiElement(val)('val')
      |    PsiWhiteSpace(' ')
      |    ListOfPatterns
      |      ReferencePattern: e
      |        PsiElement(identifier)('e')
      |    PsiWhiteSpace(' ')
      |    PsiElement(=)('=')
      |    PsiWhiteSpace(' ')
      |    MethodCall
      |      ReferenceExpression: xs.map:
      |    case 1 => 2
      |    case x => x
      |  .filter
      |        MethodCall
      |          ReferenceExpression: xs.map
      |            ReferenceExpression: xs
      |              PsiElement(identifier)('xs')
      |            PsiElement(.)('.')
      |            PsiElement(identifier)('map')
      |          ArgumentList
      |            BlockExpression
      |              PsiElement(:)(':')
      |              PsiWhiteSpace('\n    ')
      |              CaseClauses
      |                CaseClause
      |                  PsiElement(case)('case')
      |                  PsiWhiteSpace(' ')
      |                  LiteralPattern
      |                    IntegerLiteral
      |                      PsiElement(integer)('1')
      |                  PsiWhiteSpace(' ')
      |                  PsiElement(=>)('=>')
      |                  PsiWhiteSpace(' ')
      |                  BlockOfExpressions
      |                    IntegerLiteral
      |                      PsiElement(integer)('2')
      |                PsiWhiteSpace('\n    ')
      |                CaseClause
      |                  PsiElement(case)('case')
      |                  PsiWhiteSpace(' ')
      |                  ReferencePattern: x
      |                    PsiElement(identifier)('x')
      |                  PsiWhiteSpace(' ')
      |                  PsiElement(=>)('=>')
      |                  PsiWhiteSpace(' ')
      |                  BlockOfExpressions
      |                    ReferenceExpression: x
      |                      PsiElement(identifier)('x')
      |        PsiWhiteSpace('\n  ')
      |        PsiElement(.)('.')
      |        PsiElement(identifier)('filter')
      |      ArgumentList
      |        BlockExpression
      |          PsiElement(:)(':')
      |          PsiWhiteSpace(' ')
      |          FunctionExpression
      |            Parameters
      |              ParametersClause
      |                Parameter: x
      |                  PsiElement(identifier)('x')
      |            PsiWhiteSpace(' ')
      |            PsiElement(=>)('=>')
      |            PsiWhiteSpace('\n    ')
      |            BlockOfExpressions
      |              InfixExpression
      |                ReferenceExpression: x
      |                  PsiElement(identifier)('x')
      |                PsiWhiteSpace(' ')
      |                ReferenceExpression: >
      |                  PsiElement(identifier)('>')
      |                PsiWhiteSpace(' ')
      |                IntegerLiteral
      |                  PsiElement(integer)('0')
      |  PsiWhiteSpace('\n')
      |""".stripMargin
  )

  def test_fewer_braces_poly_function(): Unit = checkTree(
    """val p = xs.foo:
      |  [X] => (x: X) => x
      |""".stripMargin,
    """
      |ScalaFile
      |  ScPatternDefinition: p
      |    AnnotationsList
      |      <empty list>
      |    Modifiers
      |      <empty list>
      |    PsiElement(val)('val')
      |    PsiWhiteSpace(' ')
      |    ListOfPatterns
      |      ReferencePattern: p
      |        PsiElement(identifier)('p')
      |    PsiWhiteSpace(' ')
      |    PsiElement(=)('=')
      |    PsiWhiteSpace(' ')
      |    MethodCall
      |      ReferenceExpression: xs.foo
      |        ReferenceExpression: xs
      |          PsiElement(identifier)('xs')
      |        PsiElement(.)('.')
      |        PsiElement(identifier)('foo')
      |      ArgumentList
      |        BlockExpression
      |          PsiElement(:)(':')
      |          PsiWhiteSpace('\n  ')
      |          PolyFunctionExpression
      |            TypeParameterClause
      |              PsiElement([)('[')
      |              TypeParameter: X
      |                PsiElement(identifier)('X')
      |              PsiElement(])(']')
      |            PsiWhiteSpace(' ')
      |            PsiElement(=>)('=>')
      |            PsiWhiteSpace(' ')
      |            FunctionExpression
      |              Parameters
      |                ParametersClause
      |                  PsiElement(()('(')
      |                  Parameter: x
      |                    AnnotationsList
      |                      <empty list>
      |                    PsiElement(identifier)('x')
      |                    PsiElement(:)(':')
      |                    PsiWhiteSpace(' ')
      |                    ParameterType
      |                      SimpleType: X
      |                        CodeReferenceElement: X
      |                          PsiElement(identifier)('X')
      |                  PsiElement())(')')
      |              PsiWhiteSpace(' ')
      |              PsiElement(=>)('=>')
      |              PsiWhiteSpace(' ')
      |              BlockOfExpressions
      |                ReferenceExpression: x
      |                  PsiElement(identifier)('x')
      |  PsiWhiteSpace('\n')
      |""".stripMargin
  )

  def test_fewer_braces_poly_function2(): Unit = checkTree(
    """val p = xs.foo: [X] =>
      |  (x: X) => x
      |""".stripMargin,
    """
      |ScalaFile
      |  ScPatternDefinition: p
      |    AnnotationsList
      |      <empty list>
      |    Modifiers
      |      <empty list>
      |    PsiElement(val)('val')
      |    PsiWhiteSpace(' ')
      |    ListOfPatterns
      |      ReferencePattern: p
      |        PsiElement(identifier)('p')
      |    PsiWhiteSpace(' ')
      |    PsiElement(=)('=')
      |    PsiWhiteSpace(' ')
      |    MethodCall
      |      ReferenceExpression: xs.foo
      |        ReferenceExpression: xs
      |          PsiElement(identifier)('xs')
      |        PsiElement(.)('.')
      |        PsiElement(identifier)('foo')
      |      ArgumentList
      |        BlockExpression
      |          PsiElement(:)(':')
      |          PsiWhiteSpace(' ')
      |          PolyFunctionExpression
      |            TypeParameterClause
      |              PsiElement([)('[')
      |              TypeParameter: X
      |                PsiElement(identifier)('X')
      |              PsiElement(])(']')
      |            PsiWhiteSpace(' ')
      |            PsiElement(=>)('=>')
      |            PsiWhiteSpace('\n  ')
      |            FunctionExpression
      |              Parameters
      |                ParametersClause
      |                  PsiElement(()('(')
      |                  Parameter: x
      |                    AnnotationsList
      |                      <empty list>
      |                    PsiElement(identifier)('x')
      |                    PsiElement(:)(':')
      |                    PsiWhiteSpace(' ')
      |                    ParameterType
      |                      SimpleType: X
      |                        CodeReferenceElement: X
      |                          PsiElement(identifier)('X')
      |                  PsiElement())(')')
      |              PsiWhiteSpace(' ')
      |              PsiElement(=>)('=>')
      |              PsiWhiteSpace(' ')
      |              BlockOfExpressions
      |                ReferenceExpression: x
      |                  PsiElement(identifier)('x')
      |  PsiWhiteSpace('\n')
      |""".stripMargin
  )

  def test_fewer_braces_simple_call(): Unit = checkTree(
    """locally:
      |  y > 0
      |""".stripMargin,
    """
      |ScalaFile
      |  MethodCall
      |    ReferenceExpression: locally
      |      PsiElement(identifier)('locally')
      |    ArgumentList
      |      BlockExpression
      |        PsiElement(:)(':')
      |        PsiWhiteSpace('\n  ')
      |        InfixExpression
      |          ReferenceExpression: y
      |            PsiElement(identifier)('y')
      |          PsiWhiteSpace(' ')
      |          ReferenceExpression: >
      |            PsiElement(identifier)('>')
      |          PsiWhiteSpace(' ')
      |          IntegerLiteral
      |            PsiElement(integer)('0')
      |  PsiWhiteSpace('\n')
      |""".stripMargin
  )

  def test_fewer_braces_call_in_infix_expr(): Unit = checkTree(
    """val r = x < 0 && locally:
      |  y > 0
      |""".stripMargin,
    """
      |ScalaFile
      |  ScPatternDefinition: r
      |    AnnotationsList
      |      <empty list>
      |    Modifiers
      |      <empty list>
      |    PsiElement(val)('val')
      |    PsiWhiteSpace(' ')
      |    ListOfPatterns
      |      ReferencePattern: r
      |        PsiElement(identifier)('r')
      |    PsiWhiteSpace(' ')
      |    PsiElement(=)('=')
      |    PsiWhiteSpace(' ')
      |    InfixExpression
      |      InfixExpression
      |        ReferenceExpression: x
      |          PsiElement(identifier)('x')
      |        PsiWhiteSpace(' ')
      |        ReferenceExpression: <
      |          PsiElement(identifier)('<')
      |        PsiWhiteSpace(' ')
      |        IntegerLiteral
      |          PsiElement(integer)('0')
      |      PsiWhiteSpace(' ')
      |      ReferenceExpression: &&
      |        PsiElement(identifier)('&&')
      |      PsiWhiteSpace(' ')
      |      MethodCall
      |        ReferenceExpression: locally
      |          PsiElement(identifier)('locally')
      |        ArgumentList
      |          BlockExpression
      |            PsiElement(:)(':')
      |            PsiWhiteSpace('\n  ')
      |            InfixExpression
      |              ReferenceExpression: y
      |                PsiElement(identifier)('y')
      |              PsiWhiteSpace(' ')
      |              ReferenceExpression: >
      |                PsiElement(identifier)('>')
      |              PsiWhiteSpace(' ')
      |              IntegerLiteral
      |                PsiElement(integer)('0')
      |  PsiWhiteSpace('\n')
      |""".stripMargin
  )

  def test_fewer_braces_multiple_exprs_block_closes_on_outdent(): Unit = checkTree(
    """object Test:
      |  xo.fold:
      |    2
      |  .apply: x =>
      |    x + 2
      |
      |  xo.map: x =>
      |    x - 2
      |""".stripMargin,
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
      |    ExtendsBlock
      |      ScTemplateBody
      |        PsiElement(:)(':')
      |        PsiWhiteSpace('\n  ')
      |        MethodCall
      |          ReferenceExpression: xo.fold:
      |    2
      |  .apply
      |            MethodCall
      |              ReferenceExpression: xo.fold
      |                ReferenceExpression: xo
      |                  PsiElement(identifier)('xo')
      |                PsiElement(.)('.')
      |                PsiElement(identifier)('fold')
      |              ArgumentList
      |                BlockExpression
      |                  PsiElement(:)(':')
      |                  PsiWhiteSpace('\n    ')
      |                  IntegerLiteral
      |                    PsiElement(integer)('2')
      |            PsiWhiteSpace('\n  ')
      |            PsiElement(.)('.')
      |            PsiElement(identifier)('apply')
      |          ArgumentList
      |            BlockExpression
      |              PsiElement(:)(':')
      |              PsiWhiteSpace(' ')
      |              FunctionExpression
      |                Parameters
      |                  ParametersClause
      |                    Parameter: x
      |                      PsiElement(identifier)('x')
      |                PsiWhiteSpace(' ')
      |                PsiElement(=>)('=>')
      |                PsiWhiteSpace('\n    ')
      |                BlockOfExpressions
      |                  InfixExpression
      |                    ReferenceExpression: x
      |                      PsiElement(identifier)('x')
      |                    PsiWhiteSpace(' ')
      |                    ReferenceExpression: +
      |                      PsiElement(identifier)('+')
      |                    PsiWhiteSpace(' ')
      |                    IntegerLiteral
      |                      PsiElement(integer)('2')
      |        PsiWhiteSpace('\n\n  ')
      |        MethodCall
      |          ReferenceExpression: xo.map
      |            ReferenceExpression: xo
      |              PsiElement(identifier)('xo')
      |            PsiElement(.)('.')
      |            PsiElement(identifier)('map')
      |          ArgumentList
      |            BlockExpression
      |              PsiElement(:)(':')
      |              PsiWhiteSpace(' ')
      |              FunctionExpression
      |                Parameters
      |                  ParametersClause
      |                    Parameter: x
      |                      PsiElement(identifier)('x')
      |                PsiWhiteSpace(' ')
      |                PsiElement(=>)('=>')
      |                PsiWhiteSpace('\n    ')
      |                BlockOfExpressions
      |                  InfixExpression
      |                    ReferenceExpression: x
      |                      PsiElement(identifier)('x')
      |                    PsiWhiteSpace(' ')
      |                    ReferenceExpression: -
      |                      PsiElement(identifier)('-')
      |                    PsiWhiteSpace(' ')
      |                    IntegerLiteral
      |                      PsiElement(integer)('2')
      |  PsiWhiteSpace('\n')
      |""".stripMargin
  )
  //endregion
  // ------------------------------------------------------

  // ------------------------------------------------------
  //region Negative fewer braces parsing tests
  def test_fewer_braces_one_line_lambda__negative(): Unit = checkTree(
    """val e = xs
      |.filter: x => x > 0
      |""".stripMargin,
    """
      |ScalaFile
      |  ScPatternDefinition: e
      |    AnnotationsList
      |      <empty list>
      |    Modifiers
      |      <empty list>
      |    PsiElement(val)('val')
      |    PsiWhiteSpace(' ')
      |    ListOfPatterns
      |      ReferencePattern: e
      |        PsiElement(identifier)('e')
      |    PsiWhiteSpace(' ')
      |    PsiElement(=)('=')
      |    PsiWhiteSpace(' ')
      |    TypedExpression
      |      ReferenceExpression: xs
      |.filter
      |        ReferenceExpression: xs
      |          PsiElement(identifier)('xs')
      |        PsiWhiteSpace('\n')
      |        PsiElement(.)('.')
      |        PsiElement(identifier)('filter')
      |      PsiElement(:)(':')
      |      PsiWhiteSpace(' ')
      |      FunctionalType: x => x > 0
      |        SimpleType: x
      |          CodeReferenceElement: x
      |            PsiElement(identifier)('x')
      |        PsiWhiteSpace(' ')
      |        PsiElement(=>)('=>')
      |        PsiWhiteSpace(' ')
      |        InfixType: x > 0
      |          SimpleType: x
      |            CodeReferenceElement: x
      |              PsiElement(identifier)('x')
      |          PsiWhiteSpace(' ')
      |          CodeReferenceElement: >
      |            PsiElement(identifier)('>')
      |          PsiWhiteSpace(' ')
      |          LiteralType: 0
      |            IntegerLiteral
      |              PsiElement(integer)('0')
      |  PsiWhiteSpace('\n')
      |""".stripMargin
  )

  def test_fewer_braces_argument_after_colon__negative(): Unit = checkTree(
    """val e = xs
      |.filter: someFn
      |""".stripMargin,
    """
      |ScalaFile
      |  ScPatternDefinition: e
      |    AnnotationsList
      |      <empty list>
      |    Modifiers
      |      <empty list>
      |    PsiElement(val)('val')
      |    PsiWhiteSpace(' ')
      |    ListOfPatterns
      |      ReferencePattern: e
      |        PsiElement(identifier)('e')
      |    PsiWhiteSpace(' ')
      |    PsiElement(=)('=')
      |    PsiWhiteSpace(' ')
      |    TypedExpression
      |      ReferenceExpression: xs
      |.filter
      |        ReferenceExpression: xs
      |          PsiElement(identifier)('xs')
      |        PsiWhiteSpace('\n')
      |        PsiElement(.)('.')
      |        PsiElement(identifier)('filter')
      |      PsiElement(:)(':')
      |      PsiWhiteSpace(' ')
      |      SimpleType: someFn
      |        CodeReferenceElement: someFn
      |          PsiElement(identifier)('someFn')
      |  PsiWhiteSpace('\n')
      |""".stripMargin
  )

  def test_fewer_braces_implicit__negative(): Unit = checkTree(
    """val e = xs
      |  .filter: implicit x =>
      |    x > 0
      |""".stripMargin,
    """
      |ScalaFile
      |  ScPatternDefinition: e
      |    AnnotationsList
      |      <empty list>
      |    Modifiers
      |      <empty list>
      |    PsiElement(val)('val')
      |    PsiWhiteSpace(' ')
      |    ListOfPatterns
      |      ReferencePattern: e
      |        PsiElement(identifier)('e')
      |    PsiWhiteSpace(' ')
      |    PsiElement(=)('=')
      |    PsiWhiteSpace(' ')
      |    TypedExpression
      |      ReferenceExpression: xs
      |  .filter
      |        ReferenceExpression: xs
      |          PsiElement(identifier)('xs')
      |        PsiWhiteSpace('\n  ')
      |        PsiElement(.)('.')
      |        PsiElement(identifier)('filter')
      |      PsiElement(:)(':')
      |      AnnotationsList
      |        <empty list>
      |      PsiErrorElement:Annotation or type expected
      |        <empty list>
      |  PsiWhiteSpace(' ')
      |  FunctionExpression
      |    Parameters
      |      ParametersClause
      |        PsiElement(implicit)('implicit')
      |        PsiWhiteSpace(' ')
      |        Parameter: x
      |          PsiElement(identifier)('x')
      |    PsiWhiteSpace(' ')
      |    PsiElement(=>)('=>')
      |    PsiWhiteSpace('\n    ')
      |    InfixExpression
      |      ReferenceExpression: x
      |        PsiElement(identifier)('x')
      |      PsiWhiteSpace(' ')
      |      ReferenceExpression: >
      |        PsiElement(identifier)('>')
      |      PsiWhiteSpace(' ')
      |      IntegerLiteral
      |        PsiElement(integer)('0')
      |  PsiWhiteSpace('\n')
      |""".stripMargin
  )

  def test_fewer_braces_one_line_case_clause__negative(): Unit = checkTree(
    """xs.map: case x => x + 1
      |""".stripMargin,
    """
      |ScalaFile
      |  TypedExpression
      |    ReferenceExpression: xs.map
      |      ReferenceExpression: xs
      |        PsiElement(identifier)('xs')
      |      PsiElement(.)('.')
      |      PsiElement(identifier)('map')
      |    PsiElement(:)(':')
      |    AnnotationsList
      |      <empty list>
      |    PsiErrorElement:Annotation or type expected
      |      <empty list>
      |  PsiWhiteSpace(' ')
      |  PsiElement(case)('case')
      |  PsiWhiteSpace(' ')
      |  FunctionExpression
      |    Parameters
      |      ParametersClause
      |        Parameter: x
      |          PsiElement(identifier)('x')
      |    PsiWhiteSpace(' ')
      |    PsiElement(=>)('=>')
      |    PsiWhiteSpace(' ')
      |    InfixExpression
      |      ReferenceExpression: x
      |        PsiElement(identifier)('x')
      |      PsiWhiteSpace(' ')
      |      ReferenceExpression: +
      |        PsiElement(identifier)('+')
      |      PsiWhiteSpace(' ')
      |      IntegerLiteral
      |        PsiElement(integer)('1')
      |  PsiWhiteSpace('\n')
      |""".stripMargin
  )

  def test_fewer_braces_poly_function_one_line__negative(): Unit = checkTree(
    """val p = xs.foo: [X] => (x: X) => x
      |""".stripMargin,
    """
      |ScalaFile
      |  ScPatternDefinition: p
      |    AnnotationsList
      |      <empty list>
      |    Modifiers
      |      <empty list>
      |    PsiElement(val)('val')
      |    PsiWhiteSpace(' ')
      |    ListOfPatterns
      |      ReferencePattern: p
      |        PsiElement(identifier)('p')
      |    PsiWhiteSpace(' ')
      |    PsiElement(=)('=')
      |    PsiWhiteSpace(' ')
      |    TypedExpression
      |      ReferenceExpression: xs.foo
      |        ReferenceExpression: xs
      |          PsiElement(identifier)('xs')
      |        PsiElement(.)('.')
      |        PsiElement(identifier)('foo')
      |      PsiElement(:)(':')
      |      PsiWhiteSpace(' ')
      |      PolymorhicFunctionType: [X] => (x: X) => x
      |        TypeParameterClause
      |          PsiElement([)('[')
      |          TypeParameter: X
      |            PsiElement(identifier)('X')
      |          PsiElement(])(']')
      |        PsiWhiteSpace(' ')
      |        PsiElement(=>)('=>')
      |        PsiWhiteSpace(' ')
      |        DependentFunctionType: (x: X) => x
      |          ParametersClause
      |            PsiElement(()('(')
      |            Parameter: x
      |              PsiElement(identifier)('x')
      |              PsiElement(:)(':')
      |              PsiWhiteSpace(' ')
      |              SimpleType: X
      |                CodeReferenceElement: X
      |                  PsiElement(identifier)('X')
      |            PsiElement())(')')
      |          PsiWhiteSpace(' ')
      |          PsiElement(=>)('=>')
      |          PsiWhiteSpace(' ')
      |          SimpleType: x
      |            CodeReferenceElement: x
      |              PsiElement(identifier)('x')
      |  PsiWhiteSpace('\n')
      |""".stripMargin
  )
  //endregion
  // ------------------------------------------------------

  // ------------------------------------------------------
  //region Sanity check
  def test_fewer_braces_expr_in_parens(): Unit = checkTree(
    """val a =
      |  xs
      |  (0)
      |""".stripMargin,
    """
      |ScalaFile
      |  ScPatternDefinition: a
      |    AnnotationsList
      |      <empty list>
      |    Modifiers
      |      <empty list>
      |    PsiElement(val)('val')
      |    PsiWhiteSpace(' ')
      |    ListOfPatterns
      |      ReferencePattern: a
      |        PsiElement(identifier)('a')
      |    PsiWhiteSpace(' ')
      |    PsiElement(=)('=')
      |    BlockExpression
      |      PsiWhiteSpace('\n  ')
      |      ReferenceExpression: xs
      |        PsiElement(identifier)('xs')
      |      PsiWhiteSpace('\n  ')
      |      ExpressionInParenthesis
      |        PsiElement(()('(')
      |        IntegerLiteral
      |          PsiElement(integer)('0')
      |        PsiElement())(')')
      |  PsiWhiteSpace('\n')
      |""".stripMargin
  )

  def test_function_type(): Unit = checkTree(
    """val q = (x: String => String) => x
      |""".stripMargin,
    """
      |ScalaFile
      |  ScPatternDefinition: q
      |    AnnotationsList
      |      <empty list>
      |    Modifiers
      |      <empty list>
      |    PsiElement(val)('val')
      |    PsiWhiteSpace(' ')
      |    ListOfPatterns
      |      ReferencePattern: q
      |        PsiElement(identifier)('q')
      |    PsiWhiteSpace(' ')
      |    PsiElement(=)('=')
      |    PsiWhiteSpace(' ')
      |    FunctionExpression
      |      Parameters
      |        ParametersClause
      |          PsiElement(()('(')
      |          Parameter: x
      |            AnnotationsList
      |              <empty list>
      |            PsiElement(identifier)('x')
      |            PsiElement(:)(':')
      |            PsiWhiteSpace(' ')
      |            ParameterType
      |              FunctionalType: String => String
      |                SimpleType: String
      |                  CodeReferenceElement: String
      |                    PsiElement(identifier)('String')
      |                PsiWhiteSpace(' ')
      |                PsiElement(=>)('=>')
      |                PsiWhiteSpace(' ')
      |                SimpleType: String
      |                  CodeReferenceElement: String
      |                    PsiElement(identifier)('String')
      |          PsiElement())(')')
      |      PsiWhiteSpace(' ')
      |      PsiElement(=>)('=>')
      |      PsiWhiteSpace(' ')
      |      ReferenceExpression: x
      |        PsiElement(identifier)('x')
      |  PsiWhiteSpace('\n')
      |""".stripMargin
  )

  def test_simple_type_annotation(): Unit = checkTree(
    """(1: Int)
      |""".stripMargin,
    """
      |ScalaFile
      |  ExpressionInParenthesis
      |    PsiElement(()('(')
      |    TypedExpression
      |      IntegerLiteral
      |        PsiElement(integer)('1')
      |      PsiElement(:)(':')
      |      PsiWhiteSpace(' ')
      |      SimpleType: Int
      |        CodeReferenceElement: Int
      |          PsiElement(identifier)('Int')
      |    PsiElement())(')')
      |  PsiWhiteSpace('\n')
      |""".stripMargin
  )
  //endregion
  // ------------------------------------------------------

  // SCL-22134
  def test_color_arg_in_result_expr(): Unit = checkTree(
    """
      |giveThree: i =>
      |  giveSevenIsRed: j =>
      |    i
      |""".stripMargin,
    """
      |ScalaFile
      |  PsiWhiteSpace('\n')
      |  MethodCall
      |    ReferenceExpression: giveThree
      |      PsiElement(identifier)('giveThree')
      |    ArgumentList
      |      BlockExpression
      |        PsiElement(:)(':')
      |        PsiWhiteSpace(' ')
      |        FunctionExpression
      |          Parameters
      |            ParametersClause
      |              Parameter: i
      |                PsiElement(identifier)('i')
      |          PsiWhiteSpace(' ')
      |          PsiElement(=>)('=>')
      |          PsiWhiteSpace('\n  ')
      |          BlockOfExpressions
      |            MethodCall
      |              ReferenceExpression: giveSevenIsRed
      |                PsiElement(identifier)('giveSevenIsRed')
      |              ArgumentList
      |                BlockExpression
      |                  PsiElement(:)(':')
      |                  PsiWhiteSpace(' ')
      |                  FunctionExpression
      |                    Parameters
      |                      ParametersClause
      |                        Parameter: j
      |                          PsiElement(identifier)('j')
      |                    PsiWhiteSpace(' ')
      |                    PsiElement(=>)('=>')
      |                    PsiWhiteSpace('\n    ')
      |                    BlockOfExpressions
      |                      ReferenceExpression: i
      |                        PsiElement(identifier)('i')
      |  PsiWhiteSpace('\n')
      |""".stripMargin
  )

  def test_infix_blub(): Unit = checkTree(
    """
      |def test =
      |  1 +
      |    2
      |  println:
      |      3
      |    + 3
      |    + 3
      |
      |  println:
      |      4
      |   + 4
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
      |      InfixExpression
      |        IntegerLiteral
      |          PsiElement(integer)('1')
      |        PsiWhiteSpace(' ')
      |        ReferenceExpression: +
      |          PsiElement(identifier)('+')
      |        PsiWhiteSpace('\n    ')
      |        IntegerLiteral
      |          PsiElement(integer)('2')
      |      PsiWhiteSpace('\n  ')
      |      InfixExpression
      |        InfixExpression
      |          MethodCall
      |            ReferenceExpression: println
      |              PsiElement(identifier)('println')
      |            ArgumentList
      |              BlockExpression
      |                PsiElement(:)(':')
      |                PsiWhiteSpace('\n      ')
      |                IntegerLiteral
      |                  PsiElement(integer)('3')
      |          PsiWhiteSpace('\n    ')
      |          ReferenceExpression: +
      |            PsiElement(identifier)('+')
      |          PsiWhiteSpace(' ')
      |          IntegerLiteral
      |            PsiElement(integer)('3')
      |        PsiWhiteSpace('\n    ')
      |        ReferenceExpression: +
      |          PsiElement(identifier)('+')
      |        PsiWhiteSpace(' ')
      |        IntegerLiteral
      |          PsiElement(integer)('3')
      |      PsiWhiteSpace('\n\n  ')
      |      MethodCall
      |        ReferenceExpression: println
      |          PsiElement(identifier)('println')
      |        ArgumentList
      |          BlockExpression
      |            PsiElement(:)(':')
      |            PsiWhiteSpace('\n      ')
      |            InfixExpression
      |              IntegerLiteral
      |                PsiElement(integer)('4')
      |              PsiWhiteSpace('\n   ')
      |              ReferenceExpression: +
      |                PsiElement(identifier)('+')
      |              PsiWhiteSpace(' ')
      |              IntegerLiteral
      |                PsiElement(integer)('4')
      |  PsiWhiteSpace('\n')
      |""".stripMargin
  )

  def test_infix_outdent(): Unit = checkTree(
    """
      |class MyClass:
      |  //1
      |  Seq(1)
      |    ++ Seq(1)
      |    ++ Seq(1)
      |
      |  //2
      |  Seq(2)
      |    ++ Seq(2).map:
      |      case 2 => 2
      |    ++ Seq(2)
      |
      |  //3
      |  Seq(3)
      |    ++ Seq(3).map:
      |     x => x
      |    ++ Seq(3)
      |
      |  //4
      |  Seq(4)
      |    ++ Seq(4).map:
      |     x =>
      |       x
      |    ++ Seq(4)
      |""".stripMargin,
    """
      |ScalaFile
      |  PsiWhiteSpace('\n')
      |  ScClass: MyClass
      |    AnnotationsList
      |      <empty list>
      |    Modifiers
      |      <empty list>
      |    PsiElement(class)('class')
      |    PsiWhiteSpace(' ')
      |    PsiElement(identifier)('MyClass')
      |    PrimaryConstructor
      |      AnnotationsList
      |        <empty list>
      |      Modifiers
      |        <empty list>
      |      Parameters
      |        <empty list>
      |    ExtendsBlock
      |      ScTemplateBody
      |        PsiElement(:)(':')
      |        PsiWhiteSpace('\n  ')
      |        PsiComment(comment)('//1')
      |        PsiWhiteSpace('\n  ')
      |        InfixExpression
      |          InfixExpression
      |            MethodCall
      |              ReferenceExpression: Seq
      |                PsiElement(identifier)('Seq')
      |              ArgumentList
      |                PsiElement(()('(')
      |                IntegerLiteral
      |                  PsiElement(integer)('1')
      |                PsiElement())(')')
      |            PsiWhiteSpace('\n    ')
      |            ReferenceExpression: ++
      |              PsiElement(identifier)('++')
      |            PsiWhiteSpace(' ')
      |            MethodCall
      |              ReferenceExpression: Seq
      |                PsiElement(identifier)('Seq')
      |              ArgumentList
      |                PsiElement(()('(')
      |                IntegerLiteral
      |                  PsiElement(integer)('1')
      |                PsiElement())(')')
      |          PsiWhiteSpace('\n    ')
      |          ReferenceExpression: ++
      |            PsiElement(identifier)('++')
      |          PsiWhiteSpace(' ')
      |          MethodCall
      |            ReferenceExpression: Seq
      |              PsiElement(identifier)('Seq')
      |            ArgumentList
      |              PsiElement(()('(')
      |              IntegerLiteral
      |                PsiElement(integer)('1')
      |              PsiElement())(')')
      |        PsiWhiteSpace('\n\n  ')
      |        PsiComment(comment)('//2')
      |        PsiWhiteSpace('\n  ')
      |        InfixExpression
      |          InfixExpression
      |            MethodCall
      |              ReferenceExpression: Seq
      |                PsiElement(identifier)('Seq')
      |              ArgumentList
      |                PsiElement(()('(')
      |                IntegerLiteral
      |                  PsiElement(integer)('2')
      |                PsiElement())(')')
      |            PsiWhiteSpace('\n    ')
      |            ReferenceExpression: ++
      |              PsiElement(identifier)('++')
      |            PsiWhiteSpace(' ')
      |            MethodCall
      |              ReferenceExpression: Seq(2).map
      |                MethodCall
      |                  ReferenceExpression: Seq
      |                    PsiElement(identifier)('Seq')
      |                  ArgumentList
      |                    PsiElement(()('(')
      |                    IntegerLiteral
      |                      PsiElement(integer)('2')
      |                    PsiElement())(')')
      |                PsiElement(.)('.')
      |                PsiElement(identifier)('map')
      |              ArgumentList
      |                BlockExpression
      |                  PsiElement(:)(':')
      |                  PsiWhiteSpace('\n      ')
      |                  CaseClauses
      |                    CaseClause
      |                      PsiElement(case)('case')
      |                      PsiWhiteSpace(' ')
      |                      LiteralPattern
      |                        IntegerLiteral
      |                          PsiElement(integer)('2')
      |                      PsiWhiteSpace(' ')
      |                      PsiElement(=>)('=>')
      |                      PsiWhiteSpace(' ')
      |                      BlockOfExpressions
      |                        IntegerLiteral
      |                          PsiElement(integer)('2')
      |          PsiWhiteSpace('\n    ')
      |          ReferenceExpression: ++
      |            PsiElement(identifier)('++')
      |          PsiWhiteSpace(' ')
      |          MethodCall
      |            ReferenceExpression: Seq
      |              PsiElement(identifier)('Seq')
      |            ArgumentList
      |              PsiElement(()('(')
      |              IntegerLiteral
      |                PsiElement(integer)('2')
      |              PsiElement())(')')
      |        PsiWhiteSpace('\n\n  ')
      |        PsiComment(comment)('//3')
      |        PsiWhiteSpace('\n  ')
      |        InfixExpression
      |          InfixExpression
      |            MethodCall
      |              ReferenceExpression: Seq
      |                PsiElement(identifier)('Seq')
      |              ArgumentList
      |                PsiElement(()('(')
      |                IntegerLiteral
      |                  PsiElement(integer)('3')
      |                PsiElement())(')')
      |            PsiWhiteSpace('\n    ')
      |            ReferenceExpression: ++
      |              PsiElement(identifier)('++')
      |            PsiWhiteSpace(' ')
      |            MethodCall
      |              ReferenceExpression: Seq(3).map
      |                MethodCall
      |                  ReferenceExpression: Seq
      |                    PsiElement(identifier)('Seq')
      |                  ArgumentList
      |                    PsiElement(()('(')
      |                    IntegerLiteral
      |                      PsiElement(integer)('3')
      |                    PsiElement())(')')
      |                PsiElement(.)('.')
      |                PsiElement(identifier)('map')
      |              ArgumentList
      |                BlockExpression
      |                  PsiElement(:)(':')
      |                  PsiWhiteSpace('\n     ')
      |                  FunctionExpression
      |                    Parameters
      |                      ParametersClause
      |                        Parameter: x
      |                          PsiElement(identifier)('x')
      |                    PsiWhiteSpace(' ')
      |                    PsiElement(=>)('=>')
      |                    PsiWhiteSpace(' ')
      |                    BlockOfExpressions
      |                      ReferenceExpression: x
      |                        PsiElement(identifier)('x')
      |          PsiWhiteSpace('\n    ')
      |          ReferenceExpression: ++
      |            PsiElement(identifier)('++')
      |          PsiWhiteSpace(' ')
      |          MethodCall
      |            ReferenceExpression: Seq
      |              PsiElement(identifier)('Seq')
      |            ArgumentList
      |              PsiElement(()('(')
      |              IntegerLiteral
      |                PsiElement(integer)('3')
      |              PsiElement())(')')
      |        PsiWhiteSpace('\n\n  ')
      |        PsiComment(comment)('//4')
      |        PsiWhiteSpace('\n  ')
      |        InfixExpression
      |          InfixExpression
      |            MethodCall
      |              ReferenceExpression: Seq
      |                PsiElement(identifier)('Seq')
      |              ArgumentList
      |                PsiElement(()('(')
      |                IntegerLiteral
      |                  PsiElement(integer)('4')
      |                PsiElement())(')')
      |            PsiWhiteSpace('\n    ')
      |            ReferenceExpression: ++
      |              PsiElement(identifier)('++')
      |            PsiWhiteSpace(' ')
      |            MethodCall
      |              ReferenceExpression: Seq(4).map
      |                MethodCall
      |                  ReferenceExpression: Seq
      |                    PsiElement(identifier)('Seq')
      |                  ArgumentList
      |                    PsiElement(()('(')
      |                    IntegerLiteral
      |                      PsiElement(integer)('4')
      |                    PsiElement())(')')
      |                PsiElement(.)('.')
      |                PsiElement(identifier)('map')
      |              ArgumentList
      |                BlockExpression
      |                  PsiElement(:)(':')
      |                  PsiWhiteSpace('\n     ')
      |                  FunctionExpression
      |                    Parameters
      |                      ParametersClause
      |                        Parameter: x
      |                          PsiElement(identifier)('x')
      |                    PsiWhiteSpace(' ')
      |                    PsiElement(=>)('=>')
      |                    PsiWhiteSpace('\n       ')
      |                    BlockOfExpressions
      |                      ReferenceExpression: x
      |                        PsiElement(identifier)('x')
      |          PsiWhiteSpace('\n    ')
      |          ReferenceExpression: ++
      |            PsiElement(identifier)('++')
      |          PsiWhiteSpace(' ')
      |          MethodCall
      |            ReferenceExpression: Seq
      |              PsiElement(identifier)('Seq')
      |            ArgumentList
      |              PsiElement(()('(')
      |              IntegerLiteral
      |                PsiElement(integer)('4')
      |              PsiElement())(')')
      |  PsiWhiteSpace('\n')
      |""".stripMargin
  )

  def test_match_infix_mix(): Unit = checkTree(
    """
      |class MyClass:
      |  Seq(1)
      |    ++ Seq(2).map:
      |      case 2 => 2
      |    ++ Seq(3)
      |
      |  1
      |    + 1.match
      |      case 1 => 1
      |      case 2 => 2
      |    + 2.match
      |      case 1 => 1
      |      case 2 => 2
      |""".stripMargin,
    """
      |ScalaFile
      |  PsiWhiteSpace('\n')
      |  ScClass: MyClass
      |    AnnotationsList
      |      <empty list>
      |    Modifiers
      |      <empty list>
      |    PsiElement(class)('class')
      |    PsiWhiteSpace(' ')
      |    PsiElement(identifier)('MyClass')
      |    PrimaryConstructor
      |      AnnotationsList
      |        <empty list>
      |      Modifiers
      |        <empty list>
      |      Parameters
      |        <empty list>
      |    ExtendsBlock
      |      ScTemplateBody
      |        PsiElement(:)(':')
      |        PsiWhiteSpace('\n  ')
      |        InfixExpression
      |          InfixExpression
      |            MethodCall
      |              ReferenceExpression: Seq
      |                PsiElement(identifier)('Seq')
      |              ArgumentList
      |                PsiElement(()('(')
      |                IntegerLiteral
      |                  PsiElement(integer)('1')
      |                PsiElement())(')')
      |            PsiWhiteSpace('\n    ')
      |            ReferenceExpression: ++
      |              PsiElement(identifier)('++')
      |            PsiWhiteSpace(' ')
      |            MethodCall
      |              ReferenceExpression: Seq(2).map
      |                MethodCall
      |                  ReferenceExpression: Seq
      |                    PsiElement(identifier)('Seq')
      |                  ArgumentList
      |                    PsiElement(()('(')
      |                    IntegerLiteral
      |                      PsiElement(integer)('2')
      |                    PsiElement())(')')
      |                PsiElement(.)('.')
      |                PsiElement(identifier)('map')
      |              ArgumentList
      |                BlockExpression
      |                  PsiElement(:)(':')
      |                  PsiWhiteSpace('\n      ')
      |                  CaseClauses
      |                    CaseClause
      |                      PsiElement(case)('case')
      |                      PsiWhiteSpace(' ')
      |                      LiteralPattern
      |                        IntegerLiteral
      |                          PsiElement(integer)('2')
      |                      PsiWhiteSpace(' ')
      |                      PsiElement(=>)('=>')
      |                      PsiWhiteSpace(' ')
      |                      BlockOfExpressions
      |                        IntegerLiteral
      |                          PsiElement(integer)('2')
      |          PsiWhiteSpace('\n    ')
      |          ReferenceExpression: ++
      |            PsiElement(identifier)('++')
      |          PsiWhiteSpace(' ')
      |          MethodCall
      |            ReferenceExpression: Seq
      |              PsiElement(identifier)('Seq')
      |            ArgumentList
      |              PsiElement(()('(')
      |              IntegerLiteral
      |                PsiElement(integer)('3')
      |              PsiElement())(')')
      |        PsiWhiteSpace('\n\n  ')
      |        InfixExpression
      |          InfixExpression
      |            IntegerLiteral
      |              PsiElement(integer)('1')
      |            PsiWhiteSpace('\n    ')
      |            ReferenceExpression: +
      |              PsiElement(identifier)('+')
      |            PsiWhiteSpace(' ')
      |            MatchStatement
      |              IntegerLiteral
      |                PsiElement(integer)('1')
      |              PsiElement(.)('.')
      |              PsiElement(match)('match')
      |              PsiWhiteSpace('\n      ')
      |              CaseClauses
      |                CaseClause
      |                  PsiElement(case)('case')
      |                  PsiWhiteSpace(' ')
      |                  LiteralPattern
      |                    IntegerLiteral
      |                      PsiElement(integer)('1')
      |                  PsiWhiteSpace(' ')
      |                  PsiElement(=>)('=>')
      |                  PsiWhiteSpace(' ')
      |                  BlockOfExpressions
      |                    IntegerLiteral
      |                      PsiElement(integer)('1')
      |                PsiWhiteSpace('\n      ')
      |                CaseClause
      |                  PsiElement(case)('case')
      |                  PsiWhiteSpace(' ')
      |                  LiteralPattern
      |                    IntegerLiteral
      |                      PsiElement(integer)('2')
      |                  PsiWhiteSpace(' ')
      |                  PsiElement(=>)('=>')
      |                  PsiWhiteSpace(' ')
      |                  BlockOfExpressions
      |                    IntegerLiteral
      |                      PsiElement(integer)('2')
      |          PsiWhiteSpace('\n    ')
      |          ReferenceExpression: +
      |            PsiElement(identifier)('+')
      |          PsiWhiteSpace(' ')
      |          MatchStatement
      |            IntegerLiteral
      |              PsiElement(integer)('2')
      |            PsiElement(.)('.')
      |            PsiElement(match)('match')
      |            PsiWhiteSpace('\n      ')
      |            CaseClauses
      |              CaseClause
      |                PsiElement(case)('case')
      |                PsiWhiteSpace(' ')
      |                LiteralPattern
      |                  IntegerLiteral
      |                    PsiElement(integer)('1')
      |                PsiWhiteSpace(' ')
      |                PsiElement(=>)('=>')
      |                PsiWhiteSpace(' ')
      |                BlockOfExpressions
      |                  IntegerLiteral
      |                    PsiElement(integer)('1')
      |              PsiWhiteSpace('\n      ')
      |              CaseClause
      |                PsiElement(case)('case')
      |                PsiWhiteSpace(' ')
      |                LiteralPattern
      |                  IntegerLiteral
      |                    PsiElement(integer)('2')
      |                PsiWhiteSpace(' ')
      |                PsiElement(=>)('=>')
      |                PsiWhiteSpace(' ')
      |                BlockOfExpressions
      |                  IntegerLiteral
      |                    PsiElement(integer)('2')
      |  PsiWhiteSpace('\n')
      |""".stripMargin
  )

  // SCL-18846
  def test_outdent_before_within_definition_start(): Unit = checkTree(
    """
      |class A:
      |  @nowarn
      |
      |class B:
      |  @nowarn
      |  final*/
      |
      |final class C:
      |  @nowarn
      |  override
      |
      |def funD =
      |  @a
      |  @b
      |
      |case class E
      |""".stripMargin,
    """
      |ScalaFile
      |  PsiWhiteSpace('\n')
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
      |    ExtendsBlock
      |      ScTemplateBody
      |        PsiElement(:)(':')
      |        PsiErrorElement:Definition or declaration expected
      |          <empty list>
      |        PsiWhiteSpace('\n  ')
      |        PsiElement(@)('@')
      |        ReferenceExpression: nowarn
      |          PsiElement(identifier)('nowarn')
      |  PsiWhiteSpace('\n\n')
      |  ScClass: B
      |    AnnotationsList
      |      <empty list>
      |    Modifiers
      |      <empty list>
      |    PsiElement(class)('class')
      |    PsiWhiteSpace(' ')
      |    PsiElement(identifier)('B')
      |    PrimaryConstructor
      |      AnnotationsList
      |        <empty list>
      |      Modifiers
      |        <empty list>
      |      Parameters
      |        <empty list>
      |    ExtendsBlock
      |      ScTemplateBody
      |        PsiElement(:)(':')
      |        PsiWhiteSpace('\n  ')
      |        Annotation
      |          PsiElement(@)('@')
      |          AnnotationExpression
      |            ConstructorInvocation
      |              SimpleType: nowarn
      |                CodeReferenceElement: nowarn
      |                  PsiElement(identifier)('nowarn')
      |        PsiErrorElement:Missing statement for annotation
      |          <empty list>
      |        PsiWhiteSpace('\n  ')
      |        PsiElement(final)('final')
      |        ReferenceExpression: */
      |          PsiElement(identifier)('*/')
      |  PsiWhiteSpace('\n\n')
      |  ScClass: C
      |    AnnotationsList
      |      <empty list>
      |    Modifiers
      |      PsiElement(final)('final')
      |    PsiWhiteSpace(' ')
      |    PsiElement(class)('class')
      |    PsiWhiteSpace(' ')
      |    PsiElement(identifier)('C')
      |    PrimaryConstructor
      |      AnnotationsList
      |        <empty list>
      |      Modifiers
      |        <empty list>
      |      Parameters
      |        <empty list>
      |    ExtendsBlock
      |      ScTemplateBody
      |        PsiElement(:)(':')
      |        PsiWhiteSpace('\n  ')
      |        Annotation
      |          PsiElement(@)('@')
      |          AnnotationExpression
      |            ConstructorInvocation
      |              SimpleType: nowarn
      |                CodeReferenceElement: nowarn
      |                  PsiElement(identifier)('nowarn')
      |        PsiErrorElement:Missing statement for annotation
      |          <empty list>
      |        PsiWhiteSpace('\n  ')
      |        PsiElement(override)('override')
      |  PsiWhiteSpace('\n\n')
      |  ScFunctionDefinition: funD
      |    AnnotationsList
      |      <empty list>
      |    Modifiers
      |      <empty list>
      |    PsiElement(def)('def')
      |    PsiWhiteSpace(' ')
      |    PsiElement(identifier)('funD')
      |    Parameters
      |      <empty list>
      |    PsiWhiteSpace(' ')
      |    PsiElement(=)('=')
      |    BlockExpression
      |      PsiWhiteSpace('\n  ')
      |      Annotation
      |        PsiElement(@)('@')
      |        AnnotationExpression
      |          ConstructorInvocation
      |            SimpleType: a
      |              CodeReferenceElement: a
      |                PsiElement(identifier)('a')
      |      PsiErrorElement:Missing statement for annotation
      |        <empty list>
      |      PsiWhiteSpace('\n  ')
      |      PsiElement(@)('@')
      |      ReferenceExpression: b
      |        PsiElement(identifier)('b')
      |  PsiWhiteSpace('\n\n')
      |  ScClass: E
      |    AnnotationsList
      |      <empty list>
      |    Modifiers
      |      PsiElement(case)('case')
      |    PsiWhiteSpace(' ')
      |    PsiElement(class)('class')
      |    PsiWhiteSpace(' ')
      |    PsiElement(identifier)('E')
      |    PrimaryConstructor
      |      AnnotationsList
      |        <empty list>
      |      Modifiers
      |        <empty list>
      |      Parameters
      |        <empty list>
      |    ExtendsBlock
      |      <empty list>
      |  PsiWhiteSpace('\n')
      |""".stripMargin
  )
}
