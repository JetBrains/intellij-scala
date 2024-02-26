package org.jetbrains.plugins.scala.lang.parser.scala3

class MatchParserTest extends SimpleScala3ParserTestBase {
  def testSimple(): Unit = checkParseErrors(
    "val a: Int match { case Int => String } = ???"
  )

  def testWhitespace(): Unit = checkParseErrors(
    "type T = Int match { case Int => String case String => Int }" // OK in scalac
  )

  def testSemicolon1(): Unit = checkParseErrors(
    "type T = Int match { case Int => String; }"
  )

  def testSemicolon2(): Unit = checkParseErrors(
    "type T = Int match { case Int => String; case String => Int }"
  )

  def testSemicolon3(): Unit = checkParseErrors(
    "type T = Int match { case Int => String; case String => Int; }"
  )

  def testIndent1(): Unit = checkParseErrors(
    """type T = Int match
      |  case Int => String
      |""".stripMargin
  )

  def testIndent2(): Unit = checkParseErrors(
    """type T = Int match
      |  case Int => String
      |  case String => Int
      |""".stripMargin
  )

  def testAliasDef(): Unit = checkParseErrors(
    """
      |type Elem[X] = X match {
      |  case String      => Char
      |  case Array[t]    => t
      |  case Iterable[t] => t
      |  case AnyVal      => X
      |}
      |""".stripMargin
  )

  def testAliasWithBound(): Unit = checkParseErrors(
    """
      |type Concat[+Xs <: Tuple, +Ys <: Tuple] <: Tuple = Xs match {
      |  case Unit    => Ys
      |  case x *: xs => x *: Concat[xs, Ys]
      |}
      |""".stripMargin
  )

  def testSCL19811(): Unit = checkParseErrors(
    """
      |object A {
      |for {
      |  n <- 1 to 8 if n match {
      |  case x if x > 5 => true
      |  case _ => false
      |}
      |} yield n
      |
      |val x =
      |  123 match {
      |    case 1 => 1
      |    case 123 => 123
      |  } match {
      |    case 1 => 2
      |    case 123 => 321
      |  }
      |}
      |""".stripMargin
  )

  def testMatchExpressionPatternAlternative(): Unit = checkTree(
    "??? match { case _: Int | _: Long => }",
    """ScalaFile
      |  MatchStatement
      |    ReferenceExpression: ???
      |      PsiElement(identifier)('???')
      |    PsiWhiteSpace(' ')
      |    PsiElement(match)('match')
      |    PsiWhiteSpace(' ')
      |    PsiElement({)('{')
      |    PsiWhiteSpace(' ')
      |    CaseClauses
      |      CaseClause
      |        PsiElement(case)('case')
      |        PsiWhiteSpace(' ')
      |        CompositePattern
      |          Scala3 TypedPattern
      |            WildcardPattern
      |              PsiElement(_)('_')
      |            PsiElement(:)(':')
      |            PsiWhiteSpace(' ')
      |            TypePattern
      |              SimpleType: Int
      |                CodeReferenceElement: Int
      |                  PsiElement(identifier)('Int')
      |          PsiWhiteSpace(' ')
      |          PsiElement(identifier)('|')
      |          PsiWhiteSpace(' ')
      |          Scala3 TypedPattern
      |            WildcardPattern
      |              PsiElement(_)('_')
      |            PsiElement(:)(':')
      |            PsiWhiteSpace(' ')
      |            TypePattern
      |              SimpleType: Long
      |                CodeReferenceElement: Long
      |                  PsiElement(identifier)('Long')
      |        PsiWhiteSpace(' ')
      |        PsiElement(=>)('=>')
      |        BlockOfExpressions
      |          <empty list>
      |    PsiWhiteSpace(' ')
      |    PsiElement(})('}')""".stripMargin)

  def testMatchExpressionInfixType(): Unit = checkTree(
    "??? match { case _: (Int | Long) => }",
    """ScalaFile
      |  MatchStatement
      |    ReferenceExpression: ???
      |      PsiElement(identifier)('???')
      |    PsiWhiteSpace(' ')
      |    PsiElement(match)('match')
      |    PsiWhiteSpace(' ')
      |    PsiElement({)('{')
      |    PsiWhiteSpace(' ')
      |    CaseClauses
      |      CaseClause
      |        PsiElement(case)('case')
      |        PsiWhiteSpace(' ')
      |        Scala3 TypedPattern
      |          WildcardPattern
      |            PsiElement(_)('_')
      |          PsiElement(:)(':')
      |          PsiWhiteSpace(' ')
      |          TypePattern
      |            TypeInParenthesis: (Int | Long)
      |              PsiElement(()('(')
      |              InfixType: Int | Long
      |                SimpleType: Int
      |                  CodeReferenceElement: Int
      |                    PsiElement(identifier)('Int')
      |                PsiWhiteSpace(' ')
      |                CodeReferenceElement: |
      |                  PsiElement(identifier)('|')
      |                PsiWhiteSpace(' ')
      |                SimpleType: Long
      |                  CodeReferenceElement: Long
      |                    PsiElement(identifier)('Long')
      |              PsiElement())(')')
      |        PsiWhiteSpace(' ')
      |        PsiElement(=>)('=>')
      |        BlockOfExpressions
      |          <empty list>
      |    PsiWhiteSpace(' ')
      |    PsiElement(})('}')""".stripMargin)

  def testMatchTypeInfixType(): Unit = checkTree(
    "type T = Any match { case (Int | Long) => Nothing }",
    """ScalaFile
      |  ScTypeAliasDefinition: T
      |    AnnotationsList
      |      <empty list>
      |    Modifiers
      |      <empty list>
      |    PsiElement(type)('type')
      |    PsiWhiteSpace(' ')
      |    PsiElement(identifier)('T')
      |    PsiWhiteSpace(' ')
      |    PsiElement(=)('=')
      |    PsiWhiteSpace(' ')
      |    MatchType: Any match { case (Int | Long) => Nothing }
      |      SimpleType: Any
      |        CodeReferenceElement: Any
      |          PsiElement(identifier)('Any')
      |      PsiWhiteSpace(' ')
      |      PsiElement(match)('match')
      |      PsiWhiteSpace(' ')
      |      PsiElement({)('{')
      |      PsiWhiteSpace(' ')
      |      ScMatchTypeCasesImpl(match type cases)
      |        ScMatchTypeCaseImpl(match type case)
      |          PsiElement(case)('case')
      |          PsiWhiteSpace(' ')
      |          TypeInParenthesis: (Int | Long)
      |            PsiElement(()('(')
      |            InfixType: Int | Long
      |              SimpleType: Int
      |                CodeReferenceElement: Int
      |                  PsiElement(identifier)('Int')
      |              PsiWhiteSpace(' ')
      |              CodeReferenceElement: |
      |                PsiElement(identifier)('|')
      |              PsiWhiteSpace(' ')
      |              SimpleType: Long
      |                CodeReferenceElement: Long
      |                  PsiElement(identifier)('Long')
      |            PsiElement())(')')
      |          PsiWhiteSpace(' ')
      |          PsiElement(=>)('=>')
      |          PsiWhiteSpace(' ')
      |          SimpleType: Nothing
      |            CodeReferenceElement: Nothing
      |              PsiElement(identifier)('Nothing')
      |      PsiWhiteSpace(' ')
      |      PsiElement(})('}')""".stripMargin)

  def testMatchTypeInfixTypeWithoutParentheses(): Unit = checkTree(
    "type T = Any match { case Int | Long => Nothing }",
    """ScalaFile
      |  ScTypeAliasDefinition: T
      |    AnnotationsList
      |      <empty list>
      |    Modifiers
      |      <empty list>
      |    PsiElement(type)('type')
      |    PsiWhiteSpace(' ')
      |    PsiElement(identifier)('T')
      |    PsiWhiteSpace(' ')
      |    PsiElement(=)('=')
      |    PsiWhiteSpace(' ')
      |    MatchType: Any match { case Int | Long => Nothing }
      |      SimpleType: Any
      |        CodeReferenceElement: Any
      |          PsiElement(identifier)('Any')
      |      PsiWhiteSpace(' ')
      |      PsiElement(match)('match')
      |      PsiWhiteSpace(' ')
      |      PsiElement({)('{')
      |      PsiWhiteSpace(' ')
      |      ScMatchTypeCasesImpl(match type cases)
      |        ScMatchTypeCaseImpl(match type case)
      |          PsiElement(case)('case')
      |          PsiWhiteSpace(' ')
      |          InfixType: Int | Long
      |            SimpleType: Int
      |              CodeReferenceElement: Int
      |                PsiElement(identifier)('Int')
      |            PsiWhiteSpace(' ')
      |            CodeReferenceElement: |
      |              PsiElement(identifier)('|')
      |            PsiWhiteSpace(' ')
      |            SimpleType: Long
      |              CodeReferenceElement: Long
      |                PsiElement(identifier)('Long')
      |          PsiWhiteSpace(' ')
      |          PsiElement(=>)('=>')
      |          PsiWhiteSpace(' ')
      |          SimpleType: Nothing
      |            CodeReferenceElement: Nothing
      |              PsiElement(identifier)('Nothing')
      |      PsiWhiteSpace(' ')
      |      PsiElement(})('}')""".stripMargin)

  def testMatchExpressionTypePatternLowercase(): Unit = checkTree(
    "??? match { case _: t => }",
    """ScalaFile
      |  MatchStatement
      |    ReferenceExpression: ???
      |      PsiElement(identifier)('???')
      |    PsiWhiteSpace(' ')
      |    PsiElement(match)('match')
      |    PsiWhiteSpace(' ')
      |    PsiElement({)('{')
      |    PsiWhiteSpace(' ')
      |    CaseClauses
      |      CaseClause
      |        PsiElement(case)('case')
      |        PsiWhiteSpace(' ')
      |        Scala3 TypedPattern
      |          WildcardPattern
      |            PsiElement(_)('_')
      |          PsiElement(:)(':')
      |          PsiWhiteSpace(' ')
      |          TypePattern
      |            SimpleType: t
      |              CodeReferenceElement: t
      |                PsiElement(identifier)('t')
      |        PsiWhiteSpace(' ')
      |        PsiElement(=>)('=>')
      |        BlockOfExpressions
      |          <empty list>
      |    PsiWhiteSpace(' ')
      |    PsiElement(})('}')""".stripMargin)

/* TODO See InfixType.parseInScala3
  def testMatchTypeTypePatternLowercase(): Unit = checkTree(
    "type T = Int match { case t => Nothing }",
    """ScalaFile
      |  ScTypeAliasDefinition: T
      |    AnnotationsList
      |      <empty list>
      |    Modifiers
      |      <empty list>
      |    PsiElement(type)('type')
      |    PsiWhiteSpace(' ')
      |    PsiElement(identifier)('T')
      |    PsiWhiteSpace(' ')
      |    PsiElement(=)('=')
      |    PsiWhiteSpace(' ')
      |    MatchType: Int match { case t => Nothing }
      |      SimpleType: Int
      |        CodeReferenceElement: Int
      |          PsiElement(identifier)('Int')
      |      PsiWhiteSpace(' ')
      |      PsiElement(match)('match')
      |      PsiWhiteSpace(' ')
      |      PsiElement({)('{')
      |      PsiWhiteSpace(' ')
      |      ScMatchTypeCasesImpl(match type cases)
      |        ScMatchTypeCaseImpl(match type case)
      |          PsiElement(case)('case')
      |          PsiWhiteSpace(' ')
      |          SimpleType: t
      |            CodeReferenceElement: t
      |              PsiElement(identifier)('t')
      |          PsiWhiteSpace(' ')
      |          PsiElement(=>)('=>')
      |          PsiWhiteSpace(' ')
      |          SimpleType: Nothing
      |            CodeReferenceElement: Nothing
      |              PsiElement(identifier)('Nothing')
      |      PsiWhiteSpace(' ')
      |      PsiElement(})('}')""".stripMargin)
*/

  def testTypeVariable(): Unit = checkTree(
    "type T = Seq[Int] match { case Seq[x] => x }",
    """ScalaFile
      |  ScTypeAliasDefinition: T
      |    AnnotationsList
      |      <empty list>
      |    Modifiers
      |      <empty list>
      |    PsiElement(type)('type')
      |    PsiWhiteSpace(' ')
      |    PsiElement(identifier)('T')
      |    PsiWhiteSpace(' ')
      |    PsiElement(=)('=')
      |    PsiWhiteSpace(' ')
      |    MatchType: Seq[Int] match { case Seq[x] => x }
      |      ParametrizedType: Seq[Int]
      |        SimpleType: Seq
      |          CodeReferenceElement: Seq
      |            PsiElement(identifier)('Seq')
      |        TypeArgumentsList
      |          PsiElement([)('[')
      |          SimpleType: Int
      |            CodeReferenceElement: Int
      |              PsiElement(identifier)('Int')
      |          PsiElement(])(']')
      |      PsiWhiteSpace(' ')
      |      PsiElement(match)('match')
      |      PsiWhiteSpace(' ')
      |      PsiElement({)('{')
      |      PsiWhiteSpace(' ')
      |      ScMatchTypeCasesImpl(match type cases)
      |        ScMatchTypeCaseImpl(match type case)
      |          PsiElement(case)('case')
      |          PsiWhiteSpace(' ')
      |          ParametrizedType: Seq[x]
      |            SimpleType: Seq
      |              CodeReferenceElement: Seq
      |                PsiElement(identifier)('Seq')
      |            TypeArgumentsList
      |              PsiElement([)('[')
      |              TypeVariable: x
      |                PsiElement(identifier)('x')
      |              PsiElement(])(']')
      |          PsiWhiteSpace(' ')
      |          PsiElement(=>)('=>')
      |          PsiWhiteSpace(' ')
      |          SimpleType: x
      |            CodeReferenceElement: x
      |              PsiElement(identifier)('x')
      |      PsiWhiteSpace(' ')
      |      PsiElement(})('}')""".stripMargin)

  def testTypeVariableNested(): Unit = checkTree(
    "type T = Seq[Seq[Int]] match { case Seq[Seq[x]] => x }",
    """ScalaFile
      |  ScTypeAliasDefinition: T
      |    AnnotationsList
      |      <empty list>
      |    Modifiers
      |      <empty list>
      |    PsiElement(type)('type')
      |    PsiWhiteSpace(' ')
      |    PsiElement(identifier)('T')
      |    PsiWhiteSpace(' ')
      |    PsiElement(=)('=')
      |    PsiWhiteSpace(' ')
      |    MatchType: Seq[Seq[Int]] match { case Seq[Seq[x]] => x }
      |      ParametrizedType: Seq[Seq[Int]]
      |        SimpleType: Seq
      |          CodeReferenceElement: Seq
      |            PsiElement(identifier)('Seq')
      |        TypeArgumentsList
      |          PsiElement([)('[')
      |          ParametrizedType: Seq[Int]
      |            SimpleType: Seq
      |              CodeReferenceElement: Seq
      |                PsiElement(identifier)('Seq')
      |            TypeArgumentsList
      |              PsiElement([)('[')
      |              SimpleType: Int
      |                CodeReferenceElement: Int
      |                  PsiElement(identifier)('Int')
      |              PsiElement(])(']')
      |          PsiElement(])(']')
      |      PsiWhiteSpace(' ')
      |      PsiElement(match)('match')
      |      PsiWhiteSpace(' ')
      |      PsiElement({)('{')
      |      PsiWhiteSpace(' ')
      |      ScMatchTypeCasesImpl(match type cases)
      |        ScMatchTypeCaseImpl(match type case)
      |          PsiElement(case)('case')
      |          PsiWhiteSpace(' ')
      |          ParametrizedType: Seq[Seq[x]]
      |            SimpleType: Seq
      |              CodeReferenceElement: Seq
      |                PsiElement(identifier)('Seq')
      |            TypeArgumentsList
      |              PsiElement([)('[')
      |              ParametrizedType: Seq[x]
      |                SimpleType: Seq
      |                  CodeReferenceElement: Seq
      |                    PsiElement(identifier)('Seq')
      |                TypeArgumentsList
      |                  PsiElement([)('[')
      |                  TypeVariable: x
      |                    PsiElement(identifier)('x')
      |                  PsiElement(])(']')
      |              PsiElement(])(']')
      |          PsiWhiteSpace(' ')
      |          PsiElement(=>)('=>')
      |          PsiWhiteSpace(' ')
      |          SimpleType: x
      |            CodeReferenceElement: x
      |              PsiElement(identifier)('x')
      |      PsiWhiteSpace(' ')
      |      PsiElement(})('}')""".stripMargin)

  def testTypeVariableInfix(): Unit = checkTree(
    "type T = Int && Long match { case x && y => x }",
    """ScalaFile
      |  ScTypeAliasDefinition: T
      |    AnnotationsList
      |      <empty list>
      |    Modifiers
      |      <empty list>
      |    PsiElement(type)('type')
      |    PsiWhiteSpace(' ')
      |    PsiElement(identifier)('T')
      |    PsiWhiteSpace(' ')
      |    PsiElement(=)('=')
      |    PsiWhiteSpace(' ')
      |    MatchType: Int && Long match { case x && y => x }
      |      InfixType: Int && Long
      |        SimpleType: Int
      |          CodeReferenceElement: Int
      |            PsiElement(identifier)('Int')
      |        PsiWhiteSpace(' ')
      |        CodeReferenceElement: &&
      |          PsiElement(identifier)('&&')
      |        PsiWhiteSpace(' ')
      |        SimpleType: Long
      |          CodeReferenceElement: Long
      |            PsiElement(identifier)('Long')
      |      PsiWhiteSpace(' ')
      |      PsiElement(match)('match')
      |      PsiWhiteSpace(' ')
      |      PsiElement({)('{')
      |      PsiWhiteSpace(' ')
      |      ScMatchTypeCasesImpl(match type cases)
      |        ScMatchTypeCaseImpl(match type case)
      |          PsiElement(case)('case')
      |          PsiWhiteSpace(' ')
      |          InfixType: x && y
      |            TypeVariable: x
      |              PsiElement(identifier)('x')
      |            PsiWhiteSpace(' ')
      |            CodeReferenceElement: &&
      |              PsiElement(identifier)('&&')
      |            PsiWhiteSpace(' ')
      |            TypeVariable: y
      |              PsiElement(identifier)('y')
      |          PsiWhiteSpace(' ')
      |          PsiElement(=>)('=>')
      |          PsiWhiteSpace(' ')
      |          SimpleType: x
      |            CodeReferenceElement: x
      |              PsiElement(identifier)('x')
      |      PsiWhiteSpace(' ')
      |      PsiElement(})('}')""".stripMargin)

  // SCL-22174
  def test_comma_ending_case_clause_region(): Unit = checkTree(
    """
      |def test =
      |  func(
      |    a match
      |      case x => x,
      |    try 1
      |    catch
      |      case y => y,
      |    b match
      |      case z => z)
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
      |    PsiWhiteSpace('\n  ')
      |    MethodCall
      |      ReferenceExpression: func
      |        PsiElement(identifier)('func')
      |      ArgumentList
      |        PsiElement(()('(')
      |        PsiWhiteSpace('\n    ')
      |        MatchStatement
      |          ReferenceExpression: a
      |            PsiElement(identifier)('a')
      |          PsiWhiteSpace(' ')
      |          PsiElement(match)('match')
      |          PsiWhiteSpace('\n      ')
      |          CaseClauses
      |            CaseClause
      |              PsiElement(case)('case')
      |              PsiWhiteSpace(' ')
      |              ReferencePattern: x
      |                PsiElement(identifier)('x')
      |              PsiWhiteSpace(' ')
      |              PsiElement(=>)('=>')
      |              PsiWhiteSpace(' ')
      |              BlockOfExpressions
      |                ReferenceExpression: x
      |                  PsiElement(identifier)('x')
      |        PsiElement(,)(',')
      |        PsiWhiteSpace('\n    ')
      |        TryStatement
      |          PsiElement(try)('try')
      |          PsiWhiteSpace(' ')
      |          IntegerLiteral
      |            PsiElement(integer)('1')
      |          PsiWhiteSpace('\n    ')
      |          CatchBlock
      |            PsiElement(catch)('catch')
      |            PsiWhiteSpace('\n      ')
      |            CaseClauses
      |              CaseClause
      |                PsiElement(case)('case')
      |                PsiWhiteSpace(' ')
      |                ReferencePattern: y
      |                  PsiElement(identifier)('y')
      |                PsiWhiteSpace(' ')
      |                PsiElement(=>)('=>')
      |                PsiWhiteSpace(' ')
      |                BlockOfExpressions
      |                  ReferenceExpression: y
      |                    PsiElement(identifier)('y')
      |        PsiElement(,)(',')
      |        PsiWhiteSpace('\n    ')
      |        MatchStatement
      |          ReferenceExpression: b
      |            PsiElement(identifier)('b')
      |          PsiWhiteSpace(' ')
      |          PsiElement(match)('match')
      |          PsiWhiteSpace('\n      ')
      |          CaseClauses
      |            CaseClause
      |              PsiElement(case)('case')
      |              PsiWhiteSpace(' ')
      |              ReferencePattern: z
      |                PsiElement(identifier)('z')
      |              PsiWhiteSpace(' ')
      |              PsiElement(=>)('=>')
      |              PsiWhiteSpace(' ')
      |              BlockOfExpressions
      |                ReferenceExpression: z
      |                  PsiElement(identifier)('z')
      |        PsiElement())(')')
      |  PsiWhiteSpace('\n')
      |""".stripMargin
  )
}
