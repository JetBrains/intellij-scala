package org.jetbrains.plugins.scala.lang.parser.scala3

import org.jetbrains.plugins.scala.LatestScalaVersions

class Scala3_6_ContextBoundsTest extends SimpleScala3ParserTestBase {
  override protected def scalaVersion = LatestScalaVersions.Scala_3_6

  def testSimpleNamed(): Unit = checkTree(
    """
      |def foo[A : Ord as ord](a: A): Unit = ()
      |""".stripMargin,
    """ScalaFile
      |  PsiWhiteSpace('\n')
      |  ScFunctionDefinition: foo
      |    AnnotationsList
      |      <empty list>
      |    Modifiers
      |      <empty list>
      |    PsiElement(def)('def')
      |    PsiWhiteSpace(' ')
      |    PsiElement(identifier)('foo')
      |    TypeParameterClause
      |      PsiElement([)('[')
      |      TypeParameter: A
      |        PsiElement(identifier)('A')
      |        PsiWhiteSpace(' ')
      |        PsiElement(:)(':')
      |        PsiWhiteSpace(' ')
      |        ScContextBoundImpl(context bound)
      |          SimpleType: Ord
      |            CodeReferenceElement: Ord
      |              PsiElement(identifier)('Ord')
      |          PsiWhiteSpace(' ')
      |          PsiElement(as)('as')
      |          PsiWhiteSpace(' ')
      |          PsiElement(identifier)('ord')
      |      PsiElement(])(']')
      |    Parameters
      |      ParametersClause
      |        PsiElement(()('(')
      |        Parameter: a
      |          AnnotationsList
      |            <empty list>
      |          Modifiers
      |            <empty list>
      |          PsiElement(identifier)('a')
      |          PsiElement(:)(':')
      |          PsiWhiteSpace(' ')
      |          ParameterType
      |            SimpleType: A
      |              CodeReferenceElement: A
      |                PsiElement(identifier)('A')
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
      |  PsiWhiteSpace('\n')""".stripMargin
  )

  def testMultipleNamed(): Unit = checkTree(
    """
      |class FooImpl[T : Show as show : Ord as O](val t: T)
      |""".stripMargin,
    """ScalaFile
      |  PsiWhiteSpace('\n')
      |  ScClass: FooImpl
      |    AnnotationsList
      |      <empty list>
      |    Modifiers
      |      <empty list>
      |    PsiElement(class)('class')
      |    PsiWhiteSpace(' ')
      |    PsiElement(identifier)('FooImpl')
      |    TypeParameterClause
      |      PsiElement([)('[')
      |      TypeParameter: T
      |        PsiElement(identifier)('T')
      |        PsiWhiteSpace(' ')
      |        PsiElement(:)(':')
      |        PsiWhiteSpace(' ')
      |        ScContextBoundImpl(context bound)
      |          SimpleType: Show
      |            CodeReferenceElement: Show
      |              PsiElement(identifier)('Show')
      |          PsiWhiteSpace(' ')
      |          PsiElement(as)('as')
      |          PsiWhiteSpace(' ')
      |          PsiElement(identifier)('show')
      |        PsiWhiteSpace(' ')
      |        PsiElement(:)(':')
      |        PsiWhiteSpace(' ')
      |        ScContextBoundImpl(context bound)
      |          SimpleType: Ord
      |            CodeReferenceElement: Ord
      |              PsiElement(identifier)('Ord')
      |          PsiWhiteSpace(' ')
      |          PsiElement(as)('as')
      |          PsiWhiteSpace(' ')
      |          PsiElement(identifier)('O')
      |      PsiElement(])(']')
      |    PrimaryConstructor
      |      AnnotationsList
      |        <empty list>
      |      Modifiers
      |        <empty list>
      |      Parameters
      |        ParametersClause
      |          PsiElement(()('(')
      |          ClassParameter: t
      |            AnnotationsList
      |              <empty list>
      |            Modifiers
      |              <empty list>
      |            PsiElement(val)('val')
      |            PsiWhiteSpace(' ')
      |            PsiElement(identifier)('t')
      |            PsiElement(:)(':')
      |            PsiWhiteSpace(' ')
      |            ParameterType
      |              SimpleType: T
      |                CodeReferenceElement: T
      |                  PsiElement(identifier)('T')
      |          PsiElement())(')')
      |    ExtendsBlock
      |      <empty list>
      |  PsiWhiteSpace('\n')""".stripMargin
  )

  def testMultipleMixed(): Unit = checkTree(
    """
      |extension [Y : Ord : Show as Y](y: Y)
      |  def foo: Int = 1
      |""".stripMargin,
    """ScalaFile
      |  PsiWhiteSpace('\n')
      |  Extension on Y
      |    PsiElement(extension)('extension')
      |    PsiWhiteSpace(' ')
      |    TypeParameterClause
      |      PsiElement([)('[')
      |      TypeParameter: Y
      |        PsiElement(identifier)('Y')
      |        PsiWhiteSpace(' ')
      |        PsiElement(:)(':')
      |        PsiWhiteSpace(' ')
      |        ScContextBoundImpl(context bound)
      |          SimpleType: Ord
      |            CodeReferenceElement: Ord
      |              PsiElement(identifier)('Ord')
      |        PsiWhiteSpace(' ')
      |        PsiElement(:)(':')
      |        PsiWhiteSpace(' ')
      |        ScContextBoundImpl(context bound)
      |          SimpleType: Show
      |            CodeReferenceElement: Show
      |              PsiElement(identifier)('Show')
      |          PsiWhiteSpace(' ')
      |          PsiElement(as)('as')
      |          PsiWhiteSpace(' ')
      |          PsiElement(identifier)('Y')
      |      PsiElement(])(']')
      |    Parameters
      |      ParametersClause
      |        PsiElement(()('(')
      |        Parameter: y
      |          AnnotationsList
      |            <empty list>
      |          Modifiers
      |            <empty list>
      |          PsiElement(identifier)('y')
      |          PsiElement(:)(':')
      |          PsiWhiteSpace(' ')
      |          ParameterType
      |            SimpleType: Y
      |              CodeReferenceElement: Y
      |                PsiElement(identifier)('Y')
      |        PsiElement())(')')
      |    PsiWhiteSpace('\n  ')
      |    ScExtensionBody
      |      ScFunctionDefinition: foo
      |        AnnotationsList
      |          <empty list>
      |        Modifiers
      |          <empty list>
      |        PsiElement(def)('def')
      |        PsiWhiteSpace(' ')
      |        PsiElement(identifier)('foo')
      |        Parameters
      |          <empty list>
      |        PsiElement(:)(':')
      |        PsiWhiteSpace(' ')
      |        SimpleType: Int
      |          CodeReferenceElement: Int
      |            PsiElement(identifier)('Int')
      |        PsiWhiteSpace(' ')
      |        PsiElement(=)('=')
      |        PsiWhiteSpace(' ')
      |        IntegerLiteral
      |          PsiElement(integer)('1')
      |  PsiWhiteSpace('\n')""".stripMargin
  )

  def testAggregateBound(): Unit = checkTree(
    """
      |def foo[T : {Show, Ord, Monoid}](): Unit = ???
      |""".stripMargin,
    """ScalaFile
      |  PsiWhiteSpace('\n')
      |  ScFunctionDefinition: foo
      |    AnnotationsList
      |      <empty list>
      |    Modifiers
      |      <empty list>
      |    PsiElement(def)('def')
      |    PsiWhiteSpace(' ')
      |    PsiElement(identifier)('foo')
      |    TypeParameterClause
      |      PsiElement([)('[')
      |      TypeParameter: T
      |        PsiElement(identifier)('T')
      |        PsiWhiteSpace(' ')
      |        PsiElement(:)(':')
      |        PsiWhiteSpace(' ')
      |        PsiElement({)('{')
      |        ScContextBoundImpl(context bound)
      |          SimpleType: Show
      |            CodeReferenceElement: Show
      |              PsiElement(identifier)('Show')
      |        PsiElement(,)(',')
      |        PsiWhiteSpace(' ')
      |        ScContextBoundImpl(context bound)
      |          SimpleType: Ord
      |            CodeReferenceElement: Ord
      |              PsiElement(identifier)('Ord')
      |        PsiElement(,)(',')
      |        PsiWhiteSpace(' ')
      |        ScContextBoundImpl(context bound)
      |          SimpleType: Monoid
      |            CodeReferenceElement: Monoid
      |              PsiElement(identifier)('Monoid')
      |        PsiElement(})('}')
      |      PsiElement(])(']')
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
      |    ReferenceExpression: ???
      |      PsiElement(identifier)('???')
      |  PsiWhiteSpace('\n')""".stripMargin
  )

  def testAggregateMixed(): Unit = checkTree(
    """
      |class Bar[I : {Show as show, Ord, Monoid as M](i: I)
      |""".stripMargin,
    """ScalaFile
      |  PsiWhiteSpace('\n')
      |  ScClass: Bar
      |    AnnotationsList
      |      <empty list>
      |    Modifiers
      |      <empty list>
      |    PsiElement(class)('class')
      |    PsiWhiteSpace(' ')
      |    PsiElement(identifier)('Bar')
      |    TypeParameterClause
      |      PsiElement([)('[')
      |      TypeParameter: I
      |        PsiElement(identifier)('I')
      |        PsiWhiteSpace(' ')
      |        PsiElement(:)(':')
      |        PsiWhiteSpace(' ')
      |        PsiElement({)('{')
      |        ScContextBoundImpl(context bound)
      |          SimpleType: Show
      |            CodeReferenceElement: Show
      |              PsiElement(identifier)('Show')
      |          PsiWhiteSpace(' ')
      |          PsiElement(as)('as')
      |          PsiWhiteSpace(' ')
      |          PsiElement(identifier)('show')
      |        PsiElement(,)(',')
      |        PsiWhiteSpace(' ')
      |        ScContextBoundImpl(context bound)
      |          SimpleType: Ord
      |            CodeReferenceElement: Ord
      |              PsiElement(identifier)('Ord')
      |        PsiElement(,)(',')
      |        PsiWhiteSpace(' ')
      |        ScContextBoundImpl(context bound)
      |          SimpleType: Monoid
      |            CodeReferenceElement: Monoid
      |              PsiElement(identifier)('Monoid')
      |          PsiWhiteSpace(' ')
      |          PsiElement(as)('as')
      |          PsiWhiteSpace(' ')
      |          PsiElement(identifier)('M')
      |        PsiErrorElement:'}' expected
      |          <empty list>
      |        PsiElement(])(']')
      |      PsiErrorElement:']' expected
      |        <empty list>
      |    PrimaryConstructor
      |      AnnotationsList
      |        <empty list>
      |      Modifiers
      |        <empty list>
      |      Parameters
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
      |              SimpleType: I
      |                CodeReferenceElement: I
      |                  PsiElement(identifier)('I')
      |          PsiElement())(')')
      |    ExtendsBlock
      |      <empty list>
      |  PsiWhiteSpace('\n')""".stripMargin
  )

  def testTypeMemberBoundSimple(): Unit = checkTree(
    """
      |trait Foo {
      |  type Self : Ord
      |}
      |""".stripMargin,
    """ScalaFile
      |  PsiWhiteSpace('\n')
      |  ScTrait: Foo
      |    AnnotationsList
      |      <empty list>
      |    Modifiers
      |      <empty list>
      |    PsiElement(trait)('trait')
      |    PsiWhiteSpace(' ')
      |    PsiElement(identifier)('Foo')
      |    PsiWhiteSpace(' ')
      |    ExtendsBlock
      |      ScTemplateBody
      |        PsiElement({)('{')
      |        PsiWhiteSpace('\n  ')
      |        ScTypeAliasDeclaration: Self
      |          AnnotationsList
      |            <empty list>
      |          Modifiers
      |            <empty list>
      |          PsiElement(type)('type')
      |          PsiWhiteSpace(' ')
      |          PsiElement(identifier)('Self')
      |          PsiWhiteSpace(' ')
      |          PsiElement(:)(':')
      |          PsiWhiteSpace(' ')
      |          ScContextBoundImpl(context bound)
      |            SimpleType: Ord
      |              CodeReferenceElement: Ord
      |                PsiElement(identifier)('Ord')
      |        PsiWhiteSpace('\n')
      |        PsiElement(})('}')
      |  PsiWhiteSpace('\n')""".stripMargin
  )

  def testTypeMemberBoundAggregate(): Unit = checkTree(
    """
      |trait Bar {
      |  type Elem : { Ord as O, Comparable as comp }
      |}
      |""".stripMargin,
    """ScalaFile
      |  PsiWhiteSpace('\n')
      |  ScTrait: Bar
      |    AnnotationsList
      |      <empty list>
      |    Modifiers
      |      <empty list>
      |    PsiElement(trait)('trait')
      |    PsiWhiteSpace(' ')
      |    PsiElement(identifier)('Bar')
      |    PsiWhiteSpace(' ')
      |    ExtendsBlock
      |      ScTemplateBody
      |        PsiElement({)('{')
      |        PsiWhiteSpace('\n  ')
      |        ScTypeAliasDeclaration: Elem
      |          AnnotationsList
      |            <empty list>
      |          Modifiers
      |            <empty list>
      |          PsiElement(type)('type')
      |          PsiWhiteSpace(' ')
      |          PsiElement(identifier)('Elem')
      |          PsiWhiteSpace(' ')
      |          PsiElement(:)(':')
      |          PsiWhiteSpace(' ')
      |          PsiElement({)('{')
      |          PsiWhiteSpace(' ')
      |          ScContextBoundImpl(context bound)
      |            SimpleType: Ord
      |              CodeReferenceElement: Ord
      |                PsiElement(identifier)('Ord')
      |            PsiWhiteSpace(' ')
      |            PsiElement(as)('as')
      |            PsiWhiteSpace(' ')
      |            PsiElement(identifier)('O')
      |          PsiElement(,)(',')
      |          PsiWhiteSpace(' ')
      |          ScContextBoundImpl(context bound)
      |            SimpleType: Comparable
      |              CodeReferenceElement: Comparable
      |                PsiElement(identifier)('Comparable')
      |            PsiWhiteSpace(' ')
      |            PsiElement(as)('as')
      |            PsiWhiteSpace(' ')
      |            PsiElement(identifier)('comp')
      |          PsiWhiteSpace(' ')
      |          PsiElement(})('}')
      |        PsiWhiteSpace('\n')
      |        PsiElement(})('}')
      |  PsiWhiteSpace('\n')""".stripMargin
  )

  def testPolyFunctionType(): Unit = checkTree(
    """
      |type Comparer = [X: Ord as o] => (X, X) => Int
      |""".stripMargin,
    """ScalaFile
      |  PsiWhiteSpace('\n')
      |  ScTypeAliasDefinition: Comparer
      |    AnnotationsList
      |      <empty list>
      |    Modifiers
      |      <empty list>
      |    PsiElement(type)('type')
      |    PsiWhiteSpace(' ')
      |    PsiElement(identifier)('Comparer')
      |    PsiWhiteSpace(' ')
      |    PsiElement(=)('=')
      |    PsiWhiteSpace(' ')
      |    PolymorhicFunctionType: [X: Ord as o] => (X, X) => Int
      |      TypeParameterClause
      |        PsiElement([)('[')
      |        TypeParameter: X
      |          PsiElement(identifier)('X')
      |          PsiElement(:)(':')
      |          PsiWhiteSpace(' ')
      |          ScContextBoundImpl(context bound)
      |            SimpleType: Ord
      |              CodeReferenceElement: Ord
      |                PsiElement(identifier)('Ord')
      |            PsiWhiteSpace(' ')
      |            PsiElement(as)('as')
      |            PsiWhiteSpace(' ')
      |            PsiElement(identifier)('o')
      |        PsiElement(])(']')
      |      PsiWhiteSpace(' ')
      |      PsiElement(=>)('=>')
      |      PsiWhiteSpace(' ')
      |      FunctionalType: (X, X) => Int
      |        TupleType: (X, X)
      |          PsiElement(()('(')
      |          TypesList
      |            SimpleType: X
      |              CodeReferenceElement: X
      |                PsiElement(identifier)('X')
      |            PsiElement(,)(',')
      |            PsiWhiteSpace(' ')
      |            SimpleType: X
      |              CodeReferenceElement: X
      |                PsiElement(identifier)('X')
      |          PsiElement())(')')
      |        PsiWhiteSpace(' ')
      |        PsiElement(=>)('=>')
      |        PsiWhiteSpace(' ')
      |        SimpleType: Int
      |          CodeReferenceElement: Int
      |            PsiElement(identifier)('Int')
      |  PsiWhiteSpace('\n')""".stripMargin
  )

  def testPolyFunctionExpr(): Unit = checkTree(
    "val comparer = [X: {Ord as o, Show as s}] => (x: X, y: X) => x.compareTo(y)",
    """ScalaFile
      |  ScPatternDefinition: comparer
      |    AnnotationsList
      |      <empty list>
      |    Modifiers
      |      <empty list>
      |    PsiElement(val)('val')
      |    PsiWhiteSpace(' ')
      |    ListOfPatterns
      |      ReferencePattern: comparer
      |        PsiElement(identifier)('comparer')
      |    PsiWhiteSpace(' ')
      |    PsiElement(=)('=')
      |    PsiWhiteSpace(' ')
      |    PolyFunctionExpression
      |      TypeParameterClause
      |        PsiElement([)('[')
      |        TypeParameter: X
      |          PsiElement(identifier)('X')
      |          PsiElement(:)(':')
      |          PsiWhiteSpace(' ')
      |          PsiElement({)('{')
      |          ScContextBoundImpl(context bound)
      |            SimpleType: Ord
      |              CodeReferenceElement: Ord
      |                PsiElement(identifier)('Ord')
      |            PsiWhiteSpace(' ')
      |            PsiElement(as)('as')
      |            PsiWhiteSpace(' ')
      |            PsiElement(identifier)('o')
      |          PsiElement(,)(',')
      |          PsiWhiteSpace(' ')
      |          ScContextBoundImpl(context bound)
      |            SimpleType: Show
      |              CodeReferenceElement: Show
      |                PsiElement(identifier)('Show')
      |            PsiWhiteSpace(' ')
      |            PsiElement(as)('as')
      |            PsiWhiteSpace(' ')
      |            PsiElement(identifier)('s')
      |          PsiElement(})('}')
      |        PsiElement(])(']')
      |      PsiWhiteSpace(' ')
      |      PsiElement(=>)('=>')
      |      PsiWhiteSpace(' ')
      |      FunctionExpression
      |        Parameters
      |          ParametersClause
      |            PsiElement(()('(')
      |            Parameter: x
      |              AnnotationsList
      |                <empty list>
      |              PsiElement(identifier)('x')
      |              PsiElement(:)(':')
      |              PsiWhiteSpace(' ')
      |              ParameterType
      |                SimpleType: X
      |                  CodeReferenceElement: X
      |                    PsiElement(identifier)('X')
      |            PsiElement(,)(',')
      |            PsiWhiteSpace(' ')
      |            Parameter: y
      |              AnnotationsList
      |                <empty list>
      |              PsiElement(identifier)('y')
      |              PsiElement(:)(':')
      |              PsiWhiteSpace(' ')
      |              ParameterType
      |                SimpleType: X
      |                  CodeReferenceElement: X
      |                    PsiElement(identifier)('X')
      |            PsiElement())(')')
      |        PsiWhiteSpace(' ')
      |        PsiElement(=>)('=>')
      |        PsiWhiteSpace(' ')
      |        MethodCall
      |          ReferenceExpression: x.compareTo
      |            ReferenceExpression: x
      |              PsiElement(identifier)('x')
      |            PsiElement(.)('.')
      |            PsiElement(identifier)('compareTo')
      |          ArgumentList
      |            PsiElement(()('(')
      |            ReferenceExpression: y
      |              PsiElement(identifier)('y')
      |            PsiElement())(')')""".stripMargin
  )
}
