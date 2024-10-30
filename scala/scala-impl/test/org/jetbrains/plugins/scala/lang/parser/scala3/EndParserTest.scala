package org.jetbrains.plugins.scala.lang.parser.scala3

import com.intellij.psi.tree.IElementType
import org.jetbrains.plugins.scala.lang.lexer.{ScalaTokenType, ScalaTokenTypes}
import org.jetbrains.plugins.scala.lang.psi.api.base.ScEnd
import org.jetbrains.plugins.scala.util.PsiSelectionUtil
import org.jetbrains.plugins.scala.util.assertions.AssertionMatchers

class EndParserTest extends SimpleScala3ParserTestBase with PsiSelectionUtil with AssertionMatchers {
  def doTest(code: String, expectedType: IElementType): Unit = {
    val file = checkParseErrors(code.stripMargin)

    val endElement = searchElement[ScEnd](file)

    val designator = endElement.tag
    designator shouldNotBe null

    val designatorType = endElement.tag.getNode.getElementType
    designatorType shouldBe expectedType
  }

  def test_end_if(): Unit = doTest(
    """
      |if (boolean)
      |  stmt1
      |  stmt2
      |else
      |  stmt3
      |  stmt4
      |end if
      |""".stripMargin,
    expectedType = ScalaTokenTypes.kIF
  )


  def test_one_expr_end_if(): Unit = checkTree(
    """
      |if (boolean)
      |  stmt
      |end if
      |""".stripMargin,
    """ScalaFile
      |  PsiWhiteSpace('\n')
      |  IfStatement
      |    PsiElement(if)('if')
      |    PsiWhiteSpace(' ')
      |    PsiElement(()('(')
      |    ReferenceExpression: boolean
      |      PsiElement(identifier)('boolean')
      |    PsiElement())(')')
      |    PsiWhiteSpace('\n  ')
      |    ReferenceExpression: stmt
      |      PsiElement(identifier)('stmt')
      |    PsiWhiteSpace('\n')
      |    End: if
      |      PsiElement(end)('end')
      |      PsiWhiteSpace(' ')
      |      PsiElement(if)('if')
      |  PsiWhiteSpace('\n')
      |""".stripMargin
  )

  def test_end_on_nested_if(): Unit = checkTree(
    """
      |if (boolean)
      |  if (boolean)
      |    stmt
      |end if
      |""".stripMargin,
    """ScalaFile
      |  PsiWhiteSpace('\n')
      |  IfStatement
      |    PsiElement(if)('if')
      |    PsiWhiteSpace(' ')
      |    PsiElement(()('(')
      |    ReferenceExpression: boolean
      |      PsiElement(identifier)('boolean')
      |    PsiElement())(')')
      |    PsiWhiteSpace('\n  ')
      |    IfStatement
      |      PsiElement(if)('if')
      |      PsiWhiteSpace(' ')
      |      PsiElement(()('(')
      |      ReferenceExpression: boolean
      |        PsiElement(identifier)('boolean')
      |      PsiElement())(')')
      |      PsiWhiteSpace('\n    ')
      |      ReferenceExpression: stmt
      |        PsiElement(identifier)('stmt')
      |    PsiWhiteSpace('\n')
      |    End: if
      |      PsiElement(end)('end')
      |      PsiWhiteSpace(' ')
      |      PsiElement(if)('if')
      |  PsiWhiteSpace('\n')
      |""".stripMargin
  )

  def test_end_while(): Unit = doTest(
    """
      |while
      |  stmt1
      |  stmt2
      |do
      |  stmt3
      |  stmt4
      |end while
      |""".stripMargin,
    expectedType = ScalaTokenTypes.kWHILE
  )

  def test_end_for(): Unit = doTest(
    """
      |for
      |  x <- xs
      |do
      |  stmt1
      |end for
      |""".stripMargin,
    expectedType = ScalaTokenTypes.kFOR
  )

  def test_end_try_finally(): Unit = doTest(
    """
      |try
      |  stmt1
      |  stmt2
      |finally
      |  stmt3
      |  stmt4
      |end try
      |""".stripMargin,
    expectedType = ScalaTokenTypes.kTRY
  )

  def test_end_try_catch(): Unit = doTest(
    """
      |try
      |  stmt1
      |  stmt2
      |catch
      |case a => stmt3
      |case b => stmt4
      |end try
      |""".stripMargin,
    expectedType = ScalaTokenTypes.kTRY
  )

  def test_end_match_after_a_comment(): Unit = doTest(
    """def bar =
      |    /* hm
      |    *
      |    * */ i match
      |      case 1 => "foo"
      |      case _ => "bar"
      |    end match
      |""".stripMargin,
    expectedType = ScalaTokenTypes.kMATCH
  )

  def test_end_match(): Unit = doTest(
    """
      |something match
      |case a =>  stmt1
      |case _ => stmt2
      |end match
      |""".stripMargin,
    expectedType = ScalaTokenTypes.kMATCH
  )

  def test_end_new(): Unit = doTest(
    """
      |new:
      |  stmt1
      |  stmt2
      |end new
      |""".stripMargin,
    expectedType = ScalaTokenType.NewKeyword
  )

  def test_end_class(): Unit = doTest(
    """
      |class A:
      |  stmt1
      |  stmt2
      |end A
      |""".stripMargin,
    expectedType = ScalaTokenTypes.tIDENTIFIER
  )

  def test_end_method(): Unit = doTest(
    """
      |def test() =
      |  stmt1
      |  stmt2
      |end test
      |""".stripMargin,
    expectedType = ScalaTokenTypes.tIDENTIFIER
  )

  def test_empty_trait_end(): Unit = doTest(
    """
      |trait A:
      |end A
      |""".stripMargin,
    expectedType = ScalaTokenTypes.tIDENTIFIER
  )

  def test_empty_package_end(): Unit = doTest(
    s"""
      |package A:${err("Indented definitions expected")}
      |end A
      |""".stripMargin,
    expectedType = ScalaTokenTypes.tIDENTIFIER
  )

  def test_package_end(): Unit = checkTree(
    """package foo:
      |  package bar:
      |    object A:
      |      def foo = 1
      |  end bar
      |end foo
      |package baz:
      |  object B:
      |    def f = foo.bar.A.foo
      |end baz
      |""".stripMargin,
    """ScalaFile
      |  ScPackaging
      |    PsiElement(package)('package')
      |    PsiWhiteSpace(' ')
      |    CodeReferenceElement: foo
      |      PsiElement(identifier)('foo')
      |    PsiElement(:)(':')
      |    PsiWhiteSpace('\n  ')
      |    ScPackaging
      |      PsiElement(package)('package')
      |      PsiWhiteSpace(' ')
      |      CodeReferenceElement: bar
      |        PsiElement(identifier)('bar')
      |      PsiElement(:)(':')
      |      PsiWhiteSpace('\n    ')
      |      ScObject: A
      |        AnnotationsList
      |          <empty list>
      |        Modifiers
      |          <empty list>
      |        PsiElement(object)('object')
      |        PsiWhiteSpace(' ')
      |        PsiElement(identifier)('A')
      |        ExtendsBlock
      |          ScTemplateBody
      |            PsiElement(:)(':')
      |            PsiWhiteSpace('\n      ')
      |            ScFunctionDefinition: foo
      |              AnnotationsList
      |                <empty list>
      |              Modifiers
      |                <empty list>
      |              PsiElement(def)('def')
      |              PsiWhiteSpace(' ')
      |              PsiElement(identifier)('foo')
      |              Parameters
      |                <empty list>
      |              PsiWhiteSpace(' ')
      |              PsiElement(=)('=')
      |              PsiWhiteSpace(' ')
      |              IntegerLiteral
      |                PsiElement(integer)('1')
      |      PsiWhiteSpace('\n  ')
      |      End: bar
      |        PsiElement(end)('end')
      |        PsiWhiteSpace(' ')
      |        PsiElement(identifier)('bar')
      |    PsiWhiteSpace('\n')
      |    End: foo
      |      PsiElement(end)('end')
      |      PsiWhiteSpace(' ')
      |      PsiElement(identifier)('foo')
      |  PsiWhiteSpace('\n')
      |  ScPackaging
      |    PsiElement(package)('package')
      |    PsiWhiteSpace(' ')
      |    CodeReferenceElement: baz
      |      PsiElement(identifier)('baz')
      |    PsiElement(:)(':')
      |    PsiWhiteSpace('\n  ')
      |    ScObject: B
      |      AnnotationsList
      |        <empty list>
      |      Modifiers
      |        <empty list>
      |      PsiElement(object)('object')
      |      PsiWhiteSpace(' ')
      |      PsiElement(identifier)('B')
      |      ExtendsBlock
      |        ScTemplateBody
      |          PsiElement(:)(':')
      |          PsiWhiteSpace('\n    ')
      |          ScFunctionDefinition: f
      |            AnnotationsList
      |              <empty list>
      |            Modifiers
      |              <empty list>
      |            PsiElement(def)('def')
      |            PsiWhiteSpace(' ')
      |            PsiElement(identifier)('f')
      |            Parameters
      |              <empty list>
      |            PsiWhiteSpace(' ')
      |            PsiElement(=)('=')
      |            PsiWhiteSpace(' ')
      |            ReferenceExpression: foo.bar.A.foo
      |              ReferenceExpression: foo.bar.A
      |                ReferenceExpression: foo.bar
      |                  ReferenceExpression: foo
      |                    PsiElement(identifier)('foo')
      |                  PsiElement(.)('.')
      |                  PsiElement(identifier)('bar')
      |                PsiElement(.)('.')
      |                PsiElement(identifier)('A')
      |              PsiElement(.)('.')
      |              PsiElement(identifier)('foo')
      |    PsiWhiteSpace('\n')
      |    End: baz
      |      PsiElement(end)('end')
      |      PsiWhiteSpace(' ')
      |      PsiElement(identifier)('baz')
      |  PsiWhiteSpace('\n')
      |""".stripMargin
  )

  def testExtension(): Unit = checkTree(
    """object A {
      |  extension (c: String)
      |    def onlyDigits: Boolean = c.forall(_.isDigit)
      |  end extension
      |
      |  extension [T](xs: List[T])
      |    def sumBy[U: Numeric](f: T => U): U = ???
      |  end extension
      |}""".stripMargin,
    """ScalaFile
      |  ScObject: A
      |    AnnotationsList
      |      <empty list>
      |    Modifiers
      |      <empty list>
      |    PsiElement(object)('object')
      |    PsiWhiteSpace(' ')
      |    PsiElement(identifier)('A')
      |    PsiWhiteSpace(' ')
      |    ExtendsBlock
      |      ScTemplateBody
      |        PsiElement({)('{')
      |        PsiWhiteSpace('\n  ')
      |        Extension on String
      |          PsiElement(extension)('extension')
      |          PsiWhiteSpace(' ')
      |          Parameters
      |            ParametersClause
      |              PsiElement(()('(')
      |              Parameter: c
      |                AnnotationsList
      |                  <empty list>
      |                Modifiers
      |                  <empty list>
      |                PsiElement(identifier)('c')
      |                PsiElement(:)(':')
      |                PsiWhiteSpace(' ')
      |                ParameterType
      |                  SimpleType: String
      |                    CodeReferenceElement: String
      |                      PsiElement(identifier)('String')
      |              PsiElement())(')')
      |          PsiWhiteSpace('\n    ')
      |          ScExtensionBody
      |            ScFunctionDefinition: onlyDigits
      |              AnnotationsList
      |                <empty list>
      |              Modifiers
      |                <empty list>
      |              PsiElement(def)('def')
      |              PsiWhiteSpace(' ')
      |              PsiElement(identifier)('onlyDigits')
      |              Parameters
      |                <empty list>
      |              PsiElement(:)(':')
      |              PsiWhiteSpace(' ')
      |              SimpleType: Boolean
      |                CodeReferenceElement: Boolean
      |                  PsiElement(identifier)('Boolean')
      |              PsiWhiteSpace(' ')
      |              PsiElement(=)('=')
      |              PsiWhiteSpace(' ')
      |              MethodCall
      |                ReferenceExpression: c.forall
      |                  ReferenceExpression: c
      |                    PsiElement(identifier)('c')
      |                  PsiElement(.)('.')
      |                  PsiElement(identifier)('forall')
      |                ArgumentList
      |                  PsiElement(()('(')
      |                  ReferenceExpression: _.isDigit
      |                    UnderscoreSection
      |                      PsiElement(_)('_')
      |                    PsiElement(.)('.')
      |                    PsiElement(identifier)('isDigit')
      |                  PsiElement())(')')
      |            PsiWhiteSpace('\n  ')
      |            End: extension
      |              PsiElement(end)('end')
      |              PsiWhiteSpace(' ')
      |              PsiElement(extension)('extension')
      |        PsiWhiteSpace('\n\n  ')
      |        Extension on List[T]
      |          PsiElement(extension)('extension')
      |          PsiWhiteSpace(' ')
      |          TypeParameterClause
      |            PsiElement([)('[')
      |            TypeParameter: T
      |              PsiElement(identifier)('T')
      |            PsiElement(])(']')
      |          Parameters
      |            ParametersClause
      |              PsiElement(()('(')
      |              Parameter: xs
      |                AnnotationsList
      |                  <empty list>
      |                Modifiers
      |                  <empty list>
      |                PsiElement(identifier)('xs')
      |                PsiElement(:)(':')
      |                PsiWhiteSpace(' ')
      |                ParameterType
      |                  ParametrizedType: List[T]
      |                    SimpleType: List
      |                      CodeReferenceElement: List
      |                        PsiElement(identifier)('List')
      |                    TypeArgumentsList
      |                      PsiElement([)('[')
      |                      SimpleType: T
      |                        CodeReferenceElement: T
      |                          PsiElement(identifier)('T')
      |                      PsiElement(])(']')
      |              PsiElement())(')')
      |          PsiWhiteSpace('\n    ')
      |          ScExtensionBody
      |            ScFunctionDefinition: sumBy
      |              AnnotationsList
      |                <empty list>
      |              Modifiers
      |                <empty list>
      |              PsiElement(def)('def')
      |              PsiWhiteSpace(' ')
      |              PsiElement(identifier)('sumBy')
      |              TypeParameterClause
      |                PsiElement([)('[')
      |                TypeParameter: U
      |                  PsiElement(identifier)('U')
      |                  PsiElement(:)(':')
      |                  PsiWhiteSpace(' ')
      |                  ScContextBoundImpl(context bound)
      |                    SimpleType: Numeric
      |                      CodeReferenceElement: Numeric
      |                        PsiElement(identifier)('Numeric')
      |                PsiElement(])(']')
      |              Parameters
      |                ParametersClause
      |                  PsiElement(()('(')
      |                  Parameter: f
      |                    AnnotationsList
      |                      <empty list>
      |                    Modifiers
      |                      <empty list>
      |                    PsiElement(identifier)('f')
      |                    PsiElement(:)(':')
      |                    PsiWhiteSpace(' ')
      |                    ParameterType
      |                      FunctionalType: T => U
      |                        SimpleType: T
      |                          CodeReferenceElement: T
      |                            PsiElement(identifier)('T')
      |                        PsiWhiteSpace(' ')
      |                        PsiElement(=>)('=>')
      |                        PsiWhiteSpace(' ')
      |                        SimpleType: U
      |                          CodeReferenceElement: U
      |                            PsiElement(identifier)('U')
      |                  PsiElement())(')')
      |              PsiElement(:)(':')
      |              PsiWhiteSpace(' ')
      |              SimpleType: U
      |                CodeReferenceElement: U
      |                  PsiElement(identifier)('U')
      |              PsiWhiteSpace(' ')
      |              PsiElement(=)('=')
      |              PsiWhiteSpace(' ')
      |              ReferenceExpression: ???
      |                PsiElement(identifier)('???')
      |            PsiWhiteSpace('\n  ')
      |            End: extension
      |              PsiElement(end)('end')
      |              PsiWhiteSpace(' ')
      |              PsiElement(extension)('extension')
      |        PsiWhiteSpace('\n')
      |        PsiElement(})('}')""".stripMargin
  )

  // SCL-20880
  def test_end_marker_in_block_of_expressions(): Unit = checkTree(
    """
      |a match
      |  case b => 1 // <- to make it extra nasty
      |    class X
      |    end X
      |
      |    if c then
      |      x
      |    end if
      |""".stripMargin,
    """
      |ScalaFile
      |  PsiWhiteSpace('\n')
      |  MatchStatement
      |    ReferenceExpression: a
      |      PsiElement(identifier)('a')
      |    PsiWhiteSpace(' ')
      |    PsiElement(match)('match')
      |    PsiWhiteSpace('\n  ')
      |    CaseClauses
      |      CaseClause
      |        PsiElement(case)('case')
      |        PsiWhiteSpace(' ')
      |        ReferencePattern: b
      |          PsiElement(identifier)('b')
      |        PsiWhiteSpace(' ')
      |        PsiElement(=>)('=>')
      |        PsiWhiteSpace(' ')
      |        BlockOfExpressions
      |          IntegerLiteral
      |            PsiElement(integer)('1')
      |          PsiWhiteSpace(' ')
      |          PsiComment(comment)('// <- to make it extra nasty')
      |          PsiWhiteSpace('\n    ')
      |          ScClass: X
      |            AnnotationsList
      |              <empty list>
      |            Modifiers
      |              <empty list>
      |            PsiElement(class)('class')
      |            PsiWhiteSpace(' ')
      |            PsiElement(identifier)('X')
      |            PrimaryConstructor
      |              AnnotationsList
      |                <empty list>
      |              Modifiers
      |                <empty list>
      |              Parameters
      |                <empty list>
      |            ExtendsBlock
      |              <empty list>
      |            PsiWhiteSpace('\n    ')
      |            End: X
      |              PsiElement(end)('end')
      |              PsiWhiteSpace(' ')
      |              PsiElement(identifier)('X')
      |          PsiWhiteSpace('\n\n    ')
      |          IfStatement
      |            PsiElement(if)('if')
      |            PsiWhiteSpace(' ')
      |            ReferenceExpression: c
      |              PsiElement(identifier)('c')
      |            PsiWhiteSpace(' ')
      |            PsiElement(then)('then')
      |            PsiWhiteSpace('\n      ')
      |            ReferenceExpression: x
      |              PsiElement(identifier)('x')
      |            PsiWhiteSpace('\n    ')
      |            End: if
      |              PsiElement(end)('end')
      |              PsiWhiteSpace(' ')
      |              PsiElement(if)('if')
      |  PsiWhiteSpace('\n')
      |""".stripMargin
  )

  // #SCL-20314
  // #SCL-20311
  def test_end_marker_not_for_element_in_single_expression_region(): Unit = checkTree(
    """
      |def foo() = new Foo
      |end foo
      |""".stripMargin,
    """
      |ScalaFile
      |  PsiWhiteSpace('\n')
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
      |        PsiElement())(')')
      |    PsiWhiteSpace(' ')
      |    PsiElement(=)('=')
      |    PsiWhiteSpace(' ')
      |    ScNewTemplateDefinition: <anonymous>
      |      PsiElement(new)('new')
      |      PsiWhiteSpace(' ')
      |      ExtendsBlock
      |        TemplateParents
      |          ConstructorInvocation
      |            SimpleType: Foo
      |              CodeReferenceElement: Foo
      |                PsiElement(identifier)('Foo')
      |    PsiWhiteSpace('\n')
      |    End: foo
      |      PsiElement(end)('end')
      |      PsiWhiteSpace(' ')
      |      PsiElement(identifier)('foo')
      |  PsiWhiteSpace('\n')
      |""".stripMargin
  )

  // SCL-20681
  def test_braces_after_end(): Unit = checkTree(
    """
      |while (cond) {
      |  1 match
      |    case _ =>
      |  end match
      |}
      |
      |if (cond) {
      |  class Foo
      |  end Foo
      |}
      |""".stripMargin,
    """
      |ScalaFile
      |  PsiWhiteSpace('\n')
      |  WhileStatement
      |    PsiElement(while)('while')
      |    PsiWhiteSpace(' ')
      |    PsiElement(()('(')
      |    ReferenceExpression: cond
      |      PsiElement(identifier)('cond')
      |    PsiElement())(')')
      |    PsiWhiteSpace(' ')
      |    BlockExpression
      |      PsiElement({)('{')
      |      PsiWhiteSpace('\n  ')
      |      MatchStatement
      |        IntegerLiteral
      |          PsiElement(integer)('1')
      |        PsiWhiteSpace(' ')
      |        PsiElement(match)('match')
      |        PsiWhiteSpace('\n    ')
      |        CaseClauses
      |          CaseClause
      |            PsiElement(case)('case')
      |            PsiWhiteSpace(' ')
      |            WildcardPattern
      |              PsiElement(_)('_')
      |            PsiWhiteSpace(' ')
      |            PsiElement(=>)('=>')
      |            BlockOfExpressions
      |              <empty list>
      |        PsiWhiteSpace('\n  ')
      |        End: match
      |          PsiElement(end)('end')
      |          PsiWhiteSpace(' ')
      |          PsiElement(match)('match')
      |      PsiWhiteSpace('\n')
      |      PsiElement(})('}')
      |  PsiWhiteSpace('\n\n')
      |  IfStatement
      |    PsiElement(if)('if')
      |    PsiWhiteSpace(' ')
      |    PsiElement(()('(')
      |    ReferenceExpression: cond
      |      PsiElement(identifier)('cond')
      |    PsiElement())(')')
      |    PsiWhiteSpace(' ')
      |    BlockExpression
      |      PsiElement({)('{')
      |      PsiWhiteSpace('\n  ')
      |      ScClass: Foo
      |        AnnotationsList
      |          <empty list>
      |        Modifiers
      |          <empty list>
      |        PsiElement(class)('class')
      |        PsiWhiteSpace(' ')
      |        PsiElement(identifier)('Foo')
      |        PrimaryConstructor
      |          AnnotationsList
      |            <empty list>
      |          Modifiers
      |            <empty list>
      |          Parameters
      |            <empty list>
      |        ExtendsBlock
      |          <empty list>
      |        PsiWhiteSpace('\n  ')
      |        End: Foo
      |          PsiElement(end)('end')
      |          PsiWhiteSpace(' ')
      |          PsiElement(identifier)('Foo')
      |      PsiWhiteSpace('\n')
      |      PsiElement(})('}')
      |  PsiWhiteSpace('\n')
      |""".stripMargin
  )

  def test_end_marker_only_with_two_tokens(): Unit = checkTree(
    """
      |object end:
      |  def Test(any: Any) = 0
      |
      |class Test
      |end Test 3
      |""".stripMargin,
    """
      |ScalaFile
      |  PsiWhiteSpace('\n')
      |  ScObject: end
      |    AnnotationsList
      |      <empty list>
      |    Modifiers
      |      <empty list>
      |    PsiElement(object)('object')
      |    PsiWhiteSpace(' ')
      |    PsiElement(identifier)('end')
      |    ExtendsBlock
      |      ScTemplateBody
      |        PsiElement(:)(':')
      |        PsiWhiteSpace('\n  ')
      |        ScFunctionDefinition: Test
      |          AnnotationsList
      |            <empty list>
      |          Modifiers
      |            <empty list>
      |          PsiElement(def)('def')
      |          PsiWhiteSpace(' ')
      |          PsiElement(identifier)('Test')
      |          Parameters
      |            ParametersClause
      |              PsiElement(()('(')
      |              Parameter: any
      |                AnnotationsList
      |                  <empty list>
      |                Modifiers
      |                  <empty list>
      |                PsiElement(identifier)('any')
      |                PsiElement(:)(':')
      |                PsiWhiteSpace(' ')
      |                ParameterType
      |                  SimpleType: Any
      |                    CodeReferenceElement: Any
      |                      PsiElement(identifier)('Any')
      |              PsiElement())(')')
      |          PsiWhiteSpace(' ')
      |          PsiElement(=)('=')
      |          PsiWhiteSpace(' ')
      |          IntegerLiteral
      |            PsiElement(integer)('0')
      |  PsiWhiteSpace('\n\n')
      |  ScClass: Test
      |    AnnotationsList
      |      <empty list>
      |    Modifiers
      |      <empty list>
      |    PsiElement(class)('class')
      |    PsiWhiteSpace(' ')
      |    PsiElement(identifier)('Test')
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
      |  InfixExpression
      |    ReferenceExpression: end
      |      PsiElement(identifier)('end')
      |    PsiWhiteSpace(' ')
      |    ReferenceExpression: Test
      |      PsiElement(identifier)('Test')
      |    PsiWhiteSpace(' ')
      |    IntegerLiteral
      |      PsiElement(integer)('3')
      |  PsiWhiteSpace('\n')
      |""".stripMargin
  )

  def test_no_end_marker_for_single_expr_regions(): Unit = checkTree(
    """
      |{ if (a) 0; while (true) for (x <- y) 1
      |end while
      |}
      |""".stripMargin,
    """
      |ScalaFile
      |  PsiWhiteSpace('\n')
      |  BlockExpression
      |    PsiElement({)('{')
      |    PsiWhiteSpace(' ')
      |    IfStatement
      |      PsiElement(if)('if')
      |      PsiWhiteSpace(' ')
      |      PsiElement(()('(')
      |      ReferenceExpression: a
      |        PsiElement(identifier)('a')
      |      PsiElement())(')')
      |      PsiWhiteSpace(' ')
      |      IntegerLiteral
      |        PsiElement(integer)('0')
      |    PsiElement(;)(';')
      |    PsiWhiteSpace(' ')
      |    WhileStatement
      |      PsiElement(while)('while')
      |      PsiWhiteSpace(' ')
      |      PsiElement(()('(')
      |      BooleanLiteral
      |        PsiElement(true)('true')
      |      PsiElement())(')')
      |      PsiWhiteSpace(' ')
      |      ForStatement
      |        PsiElement(for)('for')
      |        PsiWhiteSpace(' ')
      |        PsiElement(()('(')
      |        Enumerators
      |          Generator
      |            ReferencePattern: x
      |              PsiElement(identifier)('x')
      |            PsiWhiteSpace(' ')
      |            PsiElement(<-)('<-')
      |            PsiWhiteSpace(' ')
      |            ReferenceExpression: y
      |              PsiElement(identifier)('y')
      |        PsiElement())(')')
      |        PsiWhiteSpace(' ')
      |        IntegerLiteral
      |          PsiElement(integer)('1')
      |      PsiWhiteSpace('\n')
      |      End: while
      |        PsiElement(end)('end')
      |        PsiWhiteSpace(' ')
      |        PsiElement(while)('while')
      |    PsiWhiteSpace('\n')
      |    PsiElement(})('}')
      |  PsiWhiteSpace('\n')
      |""".stripMargin
  )

  def test_end_indent_doesnt_matter_in_braced_regions(): Unit = checkTree(
    """
      |{
      |  val a =
      |    0
      |end a
      |
      |    val b =
      |      0
      |     end b
      |}
      |""".stripMargin,
    """
      |ScalaFile
      |  PsiWhiteSpace('\n')
      |  BlockExpression
      |    PsiElement({)('{')
      |    PsiWhiteSpace('\n  ')
      |    ScPatternDefinition: a
      |      AnnotationsList
      |        <empty list>
      |      Modifiers
      |        <empty list>
      |      PsiElement(val)('val')
      |      PsiWhiteSpace(' ')
      |      ListOfPatterns
      |        ReferencePattern: a
      |          PsiElement(identifier)('a')
      |      PsiWhiteSpace(' ')
      |      PsiElement(=)('=')
      |      PsiWhiteSpace('\n    ')
      |      IntegerLiteral
      |        PsiElement(integer)('0')
      |      PsiWhiteSpace('\n')
      |      End: a
      |        PsiElement(end)('end')
      |        PsiWhiteSpace(' ')
      |        PsiElement(identifier)('a')
      |    PsiWhiteSpace('\n\n    ')
      |    ScPatternDefinition: b
      |      AnnotationsList
      |        <empty list>
      |      Modifiers
      |        <empty list>
      |      PsiElement(val)('val')
      |      PsiWhiteSpace(' ')
      |      ListOfPatterns
      |        ReferencePattern: b
      |          PsiElement(identifier)('b')
      |      PsiWhiteSpace(' ')
      |      PsiElement(=)('=')
      |      PsiWhiteSpace('\n      ')
      |      IntegerLiteral
      |        PsiElement(integer)('0')
      |      PsiWhiteSpace('\n     ')
      |      End: b
      |        PsiElement(end)('end')
      |        PsiWhiteSpace(' ')
      |        PsiElement(identifier)('b')
      |    PsiWhiteSpace('\n')
      |    PsiElement(})('}')
      |  PsiWhiteSpace('\n')
      |""".stripMargin
  )

  def test_dont_parse_missing_expr_before_end(): Unit = checkTree(
    """
      |object Test:
      |  def foo =
      |end foo
      |""".stripMargin,
    """
      |ScalaFile
      |  PsiWhiteSpace('\n')
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
      |          PsiWhiteSpace(' ')
      |          PsiElement(=)('=')
      |          PsiErrorElement:Expression expected
      |            <empty list>
      |        PsiWhiteSpace('\n')
      |        End: foo
      |          PsiElement(end)('end')
      |          PsiWhiteSpace(' ')
      |          PsiElement(identifier)('foo')
      |  PsiWhiteSpace('\n')
      |""".stripMargin
  )

  // todo: add tests for given
}
