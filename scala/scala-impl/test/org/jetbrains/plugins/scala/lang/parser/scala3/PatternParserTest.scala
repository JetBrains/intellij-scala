package org.jetbrains.plugins.scala.lang.parser.scala3

import org.jetbrains.plugins.scala.ScalaVersion


class PatternParserTest extends SimpleScala3ParserTestBase {
  override protected def scalaVersion: ScalaVersion = ScalaVersion.Latest.Scala_3_7

  def test_named_constructor_arg_patterns(): Unit = checkTree(
    """
      |x match {
      |  case Foo(a = x) =>
      |  case Foo(a = x, b = y) =>
      |  case Foo(a =  , b = y) =>
      |  case Foo(a =  , b =  ) =>
      |  case Foo(  = x,   = y) =>
      |  case Foo(a,     b = y) =>
      |  case Foo(a = x, b    ) =>
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
      |        ConstructorPattern
      |          CodeReferenceElement: Foo
      |            PsiElement(identifier)('Foo')
      |          Pattern Argument List
      |            PsiElement(()('(')
      |            ScNamedConstructorArgPatternImpl(named constructor argument pattern)
      |              PsiElement(identifier)('a')
      |              PsiWhiteSpace(' ')
      |              PsiElement(=)('=')
      |              PsiWhiteSpace(' ')
      |              ReferencePattern: x
      |                PsiElement(identifier)('x')
      |            PsiElement())(')')
      |        PsiWhiteSpace(' ')
      |        PsiElement(=>)('=>')
      |        BlockOfExpressions
      |          <empty list>
      |      PsiWhiteSpace('\n  ')
      |      CaseClause
      |        PsiElement(case)('case')
      |        PsiWhiteSpace(' ')
      |        ConstructorPattern
      |          CodeReferenceElement: Foo
      |            PsiElement(identifier)('Foo')
      |          Pattern Argument List
      |            PsiElement(()('(')
      |            ScNamedConstructorArgPatternImpl(named constructor argument pattern)
      |              PsiElement(identifier)('a')
      |              PsiWhiteSpace(' ')
      |              PsiElement(=)('=')
      |              PsiWhiteSpace(' ')
      |              ReferencePattern: x
      |                PsiElement(identifier)('x')
      |            PsiElement(,)(',')
      |            PsiWhiteSpace(' ')
      |            ScNamedConstructorArgPatternImpl(named constructor argument pattern)
      |              PsiElement(identifier)('b')
      |              PsiWhiteSpace(' ')
      |              PsiElement(=)('=')
      |              PsiWhiteSpace(' ')
      |              ReferencePattern: y
      |                PsiElement(identifier)('y')
      |            PsiElement())(')')
      |        PsiWhiteSpace(' ')
      |        PsiElement(=>)('=>')
      |        BlockOfExpressions
      |          <empty list>
      |      PsiWhiteSpace('\n  ')
      |      CaseClause
      |        PsiElement(case)('case')
      |        PsiWhiteSpace(' ')
      |        ConstructorPattern
      |          CodeReferenceElement: Foo
      |            PsiElement(identifier)('Foo')
      |          Pattern Argument List
      |            PsiElement(()('(')
      |            ScNamedConstructorArgPatternImpl(named constructor argument pattern)
      |              PsiElement(identifier)('a')
      |              PsiWhiteSpace(' ')
      |              PsiElement(=)('=')
      |              PsiErrorElement:Pattern expected
      |                <empty list>
      |            PsiWhiteSpace('  ')
      |            PsiElement(,)(',')
      |            PsiWhiteSpace(' ')
      |            ScNamedConstructorArgPatternImpl(named constructor argument pattern)
      |              PsiElement(identifier)('b')
      |              PsiWhiteSpace(' ')
      |              PsiElement(=)('=')
      |              PsiWhiteSpace(' ')
      |              ReferencePattern: y
      |                PsiElement(identifier)('y')
      |            PsiElement())(')')
      |        PsiWhiteSpace(' ')
      |        PsiElement(=>)('=>')
      |        BlockOfExpressions
      |          <empty list>
      |      PsiWhiteSpace('\n  ')
      |      CaseClause
      |        PsiElement(case)('case')
      |        PsiWhiteSpace(' ')
      |        ConstructorPattern
      |          CodeReferenceElement: Foo
      |            PsiElement(identifier)('Foo')
      |          Pattern Argument List
      |            PsiElement(()('(')
      |            ScNamedConstructorArgPatternImpl(named constructor argument pattern)
      |              PsiElement(identifier)('a')
      |              PsiWhiteSpace(' ')
      |              PsiElement(=)('=')
      |              PsiErrorElement:Pattern expected
      |                <empty list>
      |            PsiWhiteSpace('  ')
      |            PsiElement(,)(',')
      |            PsiWhiteSpace(' ')
      |            ScNamedConstructorArgPatternImpl(named constructor argument pattern)
      |              PsiElement(identifier)('b')
      |              PsiWhiteSpace(' ')
      |              PsiElement(=)('=')
      |              PsiErrorElement:Pattern expected
      |                <empty list>
      |            PsiWhiteSpace('  ')
      |            PsiElement())(')')
      |        PsiWhiteSpace(' ')
      |        PsiElement(=>)('=>')
      |        BlockOfExpressions
      |          <empty list>
      |      PsiWhiteSpace('\n  ')
      |      CaseClause
      |        PsiElement(case)('case')
      |        PsiWhiteSpace(' ')
      |        ConstructorPattern
      |          CodeReferenceElement: Foo
      |            PsiElement(identifier)('Foo')
      |          Pattern Argument List
      |            PsiElement(()('(')
      |            PsiWhiteSpace('  ')
      |            ScNamedConstructorArgPatternImpl(named constructor argument pattern)
      |              PsiErrorElement:Identifier expected
      |                <empty list>
      |              PsiElement(=)('=')
      |              PsiWhiteSpace(' ')
      |              ReferencePattern: x
      |                PsiElement(identifier)('x')
      |            PsiElement(,)(',')
      |            PsiWhiteSpace('   ')
      |            ScNamedConstructorArgPatternImpl(named constructor argument pattern)
      |              PsiErrorElement:Identifier expected
      |                <empty list>
      |              PsiElement(=)('=')
      |              PsiWhiteSpace(' ')
      |              ReferencePattern: y
      |                PsiElement(identifier)('y')
      |            PsiElement())(')')
      |        PsiWhiteSpace(' ')
      |        PsiElement(=>)('=>')
      |        BlockOfExpressions
      |          <empty list>
      |      PsiWhiteSpace('\n  ')
      |      CaseClause
      |        PsiElement(case)('case')
      |        PsiWhiteSpace(' ')
      |        ConstructorPattern
      |          CodeReferenceElement: Foo
      |            PsiElement(identifier)('Foo')
      |          Pattern Argument List
      |            PsiElement(()('(')
      |            ReferencePattern: a
      |              PsiElement(identifier)('a')
      |            PsiElement(,)(',')
      |            PsiWhiteSpace('     ')
      |            ScNamedConstructorArgPatternImpl(named constructor argument pattern)
      |              PsiElement(identifier)('b')
      |              PsiWhiteSpace(' ')
      |              PsiElement(=)('=')
      |              PsiWhiteSpace(' ')
      |              ReferencePattern: y
      |                PsiElement(identifier)('y')
      |            PsiElement())(')')
      |        PsiWhiteSpace(' ')
      |        PsiElement(=>)('=>')
      |        BlockOfExpressions
      |          <empty list>
      |      PsiWhiteSpace('\n  ')
      |      CaseClause
      |        PsiElement(case)('case')
      |        PsiWhiteSpace(' ')
      |        ConstructorPattern
      |          CodeReferenceElement: Foo
      |            PsiElement(identifier)('Foo')
      |          Pattern Argument List
      |            PsiElement(()('(')
      |            ScNamedConstructorArgPatternImpl(named constructor argument pattern)
      |              PsiElement(identifier)('a')
      |              PsiWhiteSpace(' ')
      |              PsiElement(=)('=')
      |              PsiWhiteSpace(' ')
      |              ReferencePattern: x
      |                PsiElement(identifier)('x')
      |            PsiElement(,)(',')
      |            PsiWhiteSpace(' ')
      |            ReferencePattern: b
      |              PsiElement(identifier)('b')
      |            PsiWhiteSpace('    ')
      |            PsiElement())(')')
      |        PsiWhiteSpace(' ')
      |        PsiElement(=>)('=>')
      |        BlockOfExpressions
      |          <empty list>
      |    PsiWhiteSpace('\n')
      |    PsiElement(})('}')
      |  PsiWhiteSpace('\n')
      |""".stripMargin,
  )
}
