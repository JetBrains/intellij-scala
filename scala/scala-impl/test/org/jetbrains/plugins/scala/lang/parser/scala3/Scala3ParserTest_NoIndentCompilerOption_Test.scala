package org.jetbrains.plugins.scala.lang.parser.scala3

import org.jetbrains.plugins.scala.ScalaVersion
import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestCase
import org.jetbrains.plugins.scala.extensions.StringExt
import org.jetbrains.plugins.scala.lang.parser.ScalaParserTestOps
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile

// NOTE 1: we need ScalaLightCodeInsightFixtureTestAdapter as a base test in order psi files are assosiated with some module
//         (currently `-no-indent` is attached to the module)
// NOTE 2: expected PSI trees with errors were not carefully-reviewed and were just taken from the parser output as is
class Scala3ParserTest_NoIndentCompilerOption_Test
  extends ScalaLightCodeInsightFixtureTestCase
    with ScalaParserTestOps {

  override protected def supportedIn(version: ScalaVersion): Boolean = version >= ScalaVersion.Latest.Scala_3_0

  override protected def additionalCompilerOptions: Seq[String] = Seq("-no-indent")

  override def parseText(text: String) = {
    myFixture.configureByText("a.scala", text.withNormalizedSeparator).asInstanceOf[ScalaFile]
  }

  def testTemplateDef(): Unit = checkTree(
    """trait A:
      |   def f: Int
      |
      |class C(x: Int) extends A:
      |   def f = x
      |
      |object O:
      |   def f = 3
      |
      |enum Color:
      |   case Red, Green, Blue
      |
      |new A:
      |   def f = 3"
      |""".stripMargin,
    """ScalaFile
      |  ScTrait: A
      |    AnnotationsList
      |      <empty list>
      |    Modifiers
      |      <empty list>
      |    PsiElement(trait)('trait')
      |    PsiWhiteSpace(' ')
      |    PsiElement(identifier)('A')
      |    ExtendsBlock
      |      <empty list>
      |  PsiErrorElement:';' or newline expected
      |    <empty list>
      |  PsiElement(:)(':')
      |  PsiWhiteSpace('\n   ')
      |  ScFunctionDeclaration: f
      |    AnnotationsList
      |      <empty list>
      |    Modifiers
      |      <empty list>
      |    PsiElement(def)('def')
      |    PsiWhiteSpace(' ')
      |    PsiElement(identifier)('f')
      |    Parameters
      |      <empty list>
      |    PsiElement(:)(':')
      |    PsiWhiteSpace(' ')
      |    SimpleType: Int
      |      CodeReferenceElement: Int
      |        PsiElement(identifier)('Int')
      |  PsiWhiteSpace('\n\n')
      |  ScClass: C
      |    AnnotationsList
      |      <empty list>
      |    Modifiers
      |      <empty list>
      |    PsiElement(class)('class')
      |    PsiWhiteSpace(' ')
      |    PsiElement(identifier)('C')
      |    PrimaryConstructor
      |      AnnotationsList
      |        <empty list>
      |      Modifiers
      |        <empty list>
      |      Parameters
      |        ParametersClause
      |          PsiElement(()('(')
      |          ClassParameter: x
      |            AnnotationsList
      |              <empty list>
      |            Modifiers
      |              <empty list>
      |            PsiElement(identifier)('x')
      |            PsiElement(:)(':')
      |            PsiWhiteSpace(' ')
      |            ParameterType
      |              SimpleType: Int
      |                CodeReferenceElement: Int
      |                  PsiElement(identifier)('Int')
      |          PsiElement())(')')
      |    PsiWhiteSpace(' ')
      |    ExtendsBlock
      |      PsiElement(extends)('extends')
      |      PsiWhiteSpace(' ')
      |      TemplateParents
      |        ConstructorInvocation
      |          SimpleType: A
      |            CodeReferenceElement: A
      |              PsiElement(identifier)('A')
      |  PsiErrorElement:';' or newline expected
      |    <empty list>
      |  PsiElement(:)(':')
      |  PsiWhiteSpace('\n   ')
      |  ScFunctionDefinition: f
      |    AnnotationsList
      |      <empty list>
      |    Modifiers
      |      <empty list>
      |    PsiElement(def)('def')
      |    PsiWhiteSpace(' ')
      |    PsiElement(identifier)('f')
      |    Parameters
      |      <empty list>
      |    PsiWhiteSpace(' ')
      |    PsiElement(=)('=')
      |    PsiWhiteSpace(' ')
      |    ReferenceExpression: x
      |      PsiElement(identifier)('x')
      |  PsiWhiteSpace('\n\n')
      |  ScObject: O
      |    AnnotationsList
      |      <empty list>
      |    Modifiers
      |      <empty list>
      |    PsiElement(object)('object')
      |    PsiWhiteSpace(' ')
      |    PsiElement(identifier)('O')
      |    ExtendsBlock
      |      <empty list>
      |  PsiErrorElement:';' or newline expected
      |    <empty list>
      |  PsiElement(:)(':')
      |  PsiWhiteSpace('\n   ')
      |  ScFunctionDefinition: f
      |    AnnotationsList
      |      <empty list>
      |    Modifiers
      |      <empty list>
      |    PsiElement(def)('def')
      |    PsiWhiteSpace(' ')
      |    PsiElement(identifier)('f')
      |    Parameters
      |      <empty list>
      |    PsiWhiteSpace(' ')
      |    PsiElement(=)('=')
      |    PsiWhiteSpace(' ')
      |    IntegerLiteral
      |      PsiElement(integer)('3')
      |  PsiWhiteSpace('\n\n')
      |  ScEnum: Color
      |    AnnotationsList
      |      <empty list>
      |    Modifiers
      |      <empty list>
      |    PsiElement(enum)('enum')
      |    PsiWhiteSpace(' ')
      |    PsiElement(identifier)('Color')
      |    PrimaryConstructor
      |      AnnotationsList
      |        <empty list>
      |      Modifiers
      |        <empty list>
      |      Parameters
      |        <empty list>
      |    ExtendsBlock
      |      <empty list>
      |  PsiErrorElement:';' or newline expected
      |    <empty list>
      |  PsiElement(:)(':')
      |  PsiErrorElement:Wrong top statement declaration
      |    <empty list>
      |  PsiWhiteSpace('\n   ')
      |  PsiElement(case)('case')
      |  PsiWhiteSpace(' ')
      |  ReferenceExpression: Red
      |    PsiElement(identifier)('Red')
      |  PsiErrorElement:';' or newline expected
      |    <empty list>
      |  PsiElement(,)(',')
      |  PsiWhiteSpace(' ')
      |  ReferenceExpression: Green
      |    PsiElement(identifier)('Green')
      |  PsiErrorElement:';' or newline expected
      |    <empty list>
      |  PsiElement(,)(',')
      |  PsiWhiteSpace(' ')
      |  ReferenceExpression: Blue
      |    PsiElement(identifier)('Blue')
      |  PsiWhiteSpace('\n\n')
      |  TypedExpression
      |    ScNewTemplateDefinition: <anonymous>
      |      PsiElement(new)('new')
      |      PsiWhiteSpace(' ')
      |      ExtendsBlock
      |        TemplateParents
      |          ConstructorInvocation
      |            SimpleType: A
      |              CodeReferenceElement: A
      |                PsiElement(identifier)('A')
      |    PsiElement(:)(':')
      |    AnnotationsList
      |      <empty list>
      |    PsiErrorElement:Annotation or type expected
      |      <empty list>
      |  PsiWhiteSpace('\n   ')
      |  ScFunctionDefinition: f
      |    AnnotationsList
      |      <empty list>
      |    Modifiers
      |      <empty list>
      |    PsiElement(def)('def')
      |    PsiWhiteSpace(' ')
      |    PsiElement(identifier)('f')
      |    Parameters
      |      <empty list>
      |    PsiWhiteSpace(' ')
      |    PsiElement(=)('=')
      |    PsiWhiteSpace(' ')
      |    IntegerLiteral
      |      PsiElement(integer)('3')
      |  PsiErrorElement:';' or newline expected
      |    <empty list>
      |  StringLiteral
      |    PsiElement(wrong string content)('"')
      |    PsiErrorElement:Wrong string literal
      |      <empty list>
      |  PsiWhiteSpace('\n')""".stripMargin
  )

  def testPackage(): Unit = checkTree(
    """package p:
      |   def a = 1
      |""".stripMargin,
    """ScalaFile
      |  ScPackaging
      |    PsiElement(package)('package')
      |    PsiWhiteSpace(' ')
      |    CodeReferenceElement: p
      |      PsiElement(identifier)('p')
      |    PsiErrorElement:'{' expected
      |      <empty list>
      |    PsiElement(:)(':')
      |    PsiWhiteSpace('\n   ')
      |    ScFunctionDefinition: a
      |      AnnotationsList
      |        <empty list>
      |      Modifiers
      |        <empty list>
      |      PsiElement(def)('def')
      |      PsiWhiteSpace(' ')
      |      PsiElement(identifier)('a')
      |      Parameters
      |        <empty list>
      |      PsiWhiteSpace(' ')
      |      PsiElement(=)('=')
      |      PsiWhiteSpace(' ')
      |      IntegerLiteral
      |        PsiElement(integer)('1')
      |  PsiWhiteSpace('\n')""".stripMargin
  )

  // no errors expected in the parser
  def testAfterAssignOrArrow(): Unit = checkTree(
    """class Wrapper {
      |  // definitions
      |  def test =
      |    ???
      |    ???
      |  val test =
      |    ???
      |    ???
      |  var test =
      |    ???
      |    ???
      |  class A {
      |    def this(x: Int) =
      |      this()
      |      ???
      |  }
      |
      |  // assigment
      |  obj.id =
      |    ???
      |    ???
      |  map(key) =
      |    ???
      |    ???
      |
      |  given stringParser: StringParser[String] =
      |    ???
      |    ???
      |
      |  // function literal & context function literal
      |  val test = (param: Int) =>
      |    ???
      |    ???
      |  val test = (param: Int) ?=>
      |    ???
      |    ???
      |}
      |""".stripMargin,
    """ScalaFile
      |  ScClass: Wrapper
      |    AnnotationsList
      |      <empty list>
      |    Modifiers
      |      <empty list>
      |    PsiElement(class)('class')
      |    PsiWhiteSpace(' ')
      |    PsiElement(identifier)('Wrapper')
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
      |        ScFunctionDefinition: test
      |          PsiComment(comment)('// definitions')
      |          PsiWhiteSpace('\n  ')
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
      |          PsiWhiteSpace('\n    ')
      |          ReferenceExpression: ???
      |            PsiElement(identifier)('???')
      |        PsiWhiteSpace('\n    ')
      |        ReferenceExpression: ???
      |          PsiElement(identifier)('???')
      |        PsiWhiteSpace('\n  ')
      |        ScPatternDefinition: test
      |          AnnotationsList
      |            <empty list>
      |          Modifiers
      |            <empty list>
      |          PsiElement(val)('val')
      |          PsiWhiteSpace(' ')
      |          ListOfPatterns
      |            ReferencePattern: test
      |              PsiElement(identifier)('test')
      |          PsiWhiteSpace(' ')
      |          PsiElement(=)('=')
      |          PsiWhiteSpace('\n    ')
      |          ReferenceExpression: ???
      |            PsiElement(identifier)('???')
      |        PsiWhiteSpace('\n    ')
      |        ReferenceExpression: ???
      |          PsiElement(identifier)('???')
      |        PsiWhiteSpace('\n  ')
      |        ScVariableDefinition: test
      |          AnnotationsList
      |            <empty list>
      |          Modifiers
      |            <empty list>
      |          PsiElement(var)('var')
      |          PsiWhiteSpace(' ')
      |          ListOfPatterns
      |            ReferencePattern: test
      |              PsiElement(identifier)('test')
      |          PsiWhiteSpace(' ')
      |          PsiElement(=)('=')
      |          PsiWhiteSpace('\n    ')
      |          ReferenceExpression: ???
      |            PsiElement(identifier)('???')
      |        PsiWhiteSpace('\n    ')
      |        ReferenceExpression: ???
      |          PsiElement(identifier)('???')
      |        PsiWhiteSpace('\n  ')
      |        ScClass: A
      |          AnnotationsList
      |            <empty list>
      |          Modifiers
      |            <empty list>
      |          PsiElement(class)('class')
      |          PsiWhiteSpace(' ')
      |          PsiElement(identifier)('A')
      |          PrimaryConstructor
      |            AnnotationsList
      |              <empty list>
      |            Modifiers
      |              <empty list>
      |            Parameters
      |              <empty list>
      |          PsiWhiteSpace(' ')
      |          ExtendsBlock
      |            ScTemplateBody
      |              PsiElement({)('{')
      |              PsiWhiteSpace('\n    ')
      |              ScFunctionDefinition: this
      |                AnnotationsList
      |                  <empty list>
      |                Modifiers
      |                  <empty list>
      |                PsiElement(def)('def')
      |                PsiWhiteSpace(' ')
      |                PsiElement(this)('this')
      |                Parameters
      |                  ParametersClause
      |                    PsiElement(()('(')
      |                    Parameter: x
      |                      AnnotationsList
      |                        <empty list>
      |                      Modifiers
      |                        <empty list>
      |                      PsiElement(identifier)('x')
      |                      PsiElement(:)(':')
      |                      PsiWhiteSpace(' ')
      |                      ParameterType
      |                        SimpleType: Int
      |                          CodeReferenceElement: Int
      |                            PsiElement(identifier)('Int')
      |                    PsiElement())(')')
      |                PsiWhiteSpace(' ')
      |                PsiElement(=)('=')
      |                PsiWhiteSpace('\n      ')
      |                SelfInvocation
      |                  PsiElement(this)('this')
      |                  ArgumentList
      |                    PsiElement(()('(')
      |                    PsiElement())(')')
      |              PsiWhiteSpace('\n      ')
      |              ReferenceExpression: ???
      |                PsiElement(identifier)('???')
      |              PsiWhiteSpace('\n  ')
      |              PsiElement(})('}')
      |        PsiWhiteSpace('\n\n  ')
      |        PsiComment(comment)('// assigment')
      |        PsiWhiteSpace('\n  ')
      |        AssignStatement
      |          ReferenceExpression: obj.id
      |            ReferenceExpression: obj
      |              PsiElement(identifier)('obj')
      |            PsiElement(.)('.')
      |            PsiElement(identifier)('id')
      |          PsiWhiteSpace(' ')
      |          PsiElement(=)('=')
      |          PsiWhiteSpace('\n    ')
      |          ReferenceExpression: ???
      |            PsiElement(identifier)('???')
      |        PsiWhiteSpace('\n    ')
      |        ReferenceExpression: ???
      |          PsiElement(identifier)('???')
      |        PsiWhiteSpace('\n  ')
      |        AssignStatement
      |          MethodCall
      |            ReferenceExpression: map
      |              PsiElement(identifier)('map')
      |            ArgumentList
      |              PsiElement(()('(')
      |              ReferenceExpression: key
      |                PsiElement(identifier)('key')
      |              PsiElement())(')')
      |          PsiWhiteSpace(' ')
      |          PsiElement(=)('=')
      |          PsiWhiteSpace('\n    ')
      |          ReferenceExpression: ???
      |            PsiElement(identifier)('???')
      |        PsiWhiteSpace('\n    ')
      |        ReferenceExpression: ???
      |          PsiElement(identifier)('???')
      |        PsiWhiteSpace('\n\n  ')
      |        ScGivenAliasDefinition: stringParser
      |          AnnotationsList
      |            <empty list>
      |          Modifiers
      |            <empty list>
      |          PsiElement(given)('given')
      |          PsiWhiteSpace(' ')
      |          PsiElement(identifier)('stringParser')
      |          Parameters
      |            <empty list>
      |          PsiElement(:)(':')
      |          PsiWhiteSpace(' ')
      |          ParametrizedType: StringParser[String]
      |            SimpleType: StringParser
      |              CodeReferenceElement: StringParser
      |                PsiElement(identifier)('StringParser')
      |            TypeArgumentsList
      |              PsiElement([)('[')
      |              SimpleType: String
      |                CodeReferenceElement: String
      |                  PsiElement(identifier)('String')
      |              PsiElement(])(']')
      |          PsiWhiteSpace(' ')
      |          PsiElement(=)('=')
      |          PsiWhiteSpace('\n    ')
      |          ReferenceExpression: ???
      |            PsiElement(identifier)('???')
      |        PsiWhiteSpace('\n    ')
      |        ReferenceExpression: ???
      |          PsiElement(identifier)('???')
      |        PsiWhiteSpace('\n\n  ')
      |        ScPatternDefinition: test
      |          PsiComment(comment)('// function literal & context function literal')
      |          PsiWhiteSpace('\n  ')
      |          AnnotationsList
      |            <empty list>
      |          Modifiers
      |            <empty list>
      |          PsiElement(val)('val')
      |          PsiWhiteSpace(' ')
      |          ListOfPatterns
      |            ReferencePattern: test
      |              PsiElement(identifier)('test')
      |          PsiWhiteSpace(' ')
      |          PsiElement(=)('=')
      |          PsiWhiteSpace(' ')
      |          FunctionExpression
      |            Parameters
      |              ParametersClause
      |                PsiElement(()('(')
      |                Parameter: param
      |                  AnnotationsList
      |                    <empty list>
      |                  PsiElement(identifier)('param')
      |                  PsiElement(:)(':')
      |                  PsiWhiteSpace(' ')
      |                  ParameterType
      |                    SimpleType: Int
      |                      CodeReferenceElement: Int
      |                        PsiElement(identifier)('Int')
      |                PsiElement())(')')
      |            PsiWhiteSpace(' ')
      |            PsiElement(=>)('=>')
      |            PsiWhiteSpace('\n    ')
      |            ReferenceExpression: ???
      |              PsiElement(identifier)('???')
      |        PsiWhiteSpace('\n    ')
      |        ReferenceExpression: ???
      |          PsiElement(identifier)('???')
      |        PsiWhiteSpace('\n  ')
      |        ScPatternDefinition: test
      |          AnnotationsList
      |            <empty list>
      |          Modifiers
      |            <empty list>
      |          PsiElement(val)('val')
      |          PsiWhiteSpace(' ')
      |          ListOfPatterns
      |            ReferencePattern: test
      |              PsiElement(identifier)('test')
      |          PsiWhiteSpace(' ')
      |          PsiElement(=)('=')
      |          PsiWhiteSpace(' ')
      |          FunctionExpression
      |            Parameters
      |              ParametersClause
      |                PsiElement(()('(')
      |                Parameter: param
      |                  AnnotationsList
      |                    <empty list>
      |                  PsiElement(identifier)('param')
      |                  PsiElement(:)(':')
      |                  PsiWhiteSpace(' ')
      |                  ParameterType
      |                    SimpleType: Int
      |                      CodeReferenceElement: Int
      |                        PsiElement(identifier)('Int')
      |                PsiElement())(')')
      |            PsiWhiteSpace(' ')
      |            PsiElement(?=>)('?=>')
      |            PsiWhiteSpace('\n    ')
      |            ReferenceExpression: ???
      |              PsiElement(identifier)('???')
      |        PsiWhiteSpace('\n    ')
      |        ReferenceExpression: ???
      |          PsiElement(identifier)('???')
      |        PsiWhiteSpace('\n')
      |        PsiElement(})('}')
      |  PsiWhiteSpace('\n')""".stripMargin
  )

  def testForEnumeratorsShouldNotBeEffectedByNoIndentFlag(): Unit = checkParseErrors(
    """for
      |  x <- 1 to 2
      |  y <- 1 to 2
      |yield x + y
      |""".stripMargin
  )

  def testControlFlowSyntax(): Unit = checkTree(
    """class Wrapper {
      |    // if/then/else
      |    if (true)
      |       ???
      |       ???
      |    if (true) then
      |      ???
      |      ???
      |    if true then 42
      |    else
      |        ???
      |        ???
      |
      |    // for + do/yield/-
      |    for (x <- 1 to 3)
      |      ???
      |      ???
      |    for (x <- 1 to 3) do
      |      ???
      |      ???
      |    for (x <- 1 to 3) yield
      |      ???
      |      ???
      |
      |    // while + do/-
      |    while (2 * 2 == 5)
      |      ???
      |      ???
      |    while (2 * 2 == 5) do
      |      ???
      |      ???
      |
      |    /** try / finally (catch is handled in [[Scala3TestDataCaseClausesEditorStates]]) */
      |    try
      |      ???
      |      ???
      |    catch {
      |      case _ =>
      |    }
      |
      |    try 42
      |    finally
      |      ???
      |      ???
      |
      |    // throw
      |    throw
      |      ???
      |      ???
      |}""".stripMargin,
    """ScalaFile
      |  ScClass: Wrapper
      |    AnnotationsList
      |      <empty list>
      |    Modifiers
      |      <empty list>
      |    PsiElement(class)('class')
      |    PsiWhiteSpace(' ')
      |    PsiElement(identifier)('Wrapper')
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
      |        PsiWhiteSpace('\n    ')
      |        PsiComment(comment)('// if/then/else')
      |        PsiWhiteSpace('\n    ')
      |        IfStatement
      |          PsiElement(if)('if')
      |          PsiWhiteSpace(' ')
      |          PsiElement(()('(')
      |          BooleanLiteral
      |            PsiElement(true)('true')
      |          PsiElement())(')')
      |          PsiWhiteSpace('\n       ')
      |          ReferenceExpression: ???
      |            PsiElement(identifier)('???')
      |        PsiWhiteSpace('\n       ')
      |        ReferenceExpression: ???
      |          PsiElement(identifier)('???')
      |        PsiWhiteSpace('\n    ')
      |        IfStatement
      |          PsiElement(if)('if')
      |          PsiWhiteSpace(' ')
      |          ExpressionInParenthesis
      |            PsiElement(()('(')
      |            BooleanLiteral
      |              PsiElement(true)('true')
      |            PsiElement())(')')
      |          PsiWhiteSpace(' ')
      |          PsiElement(then)('then')
      |          PsiWhiteSpace('\n      ')
      |          ReferenceExpression: ???
      |            PsiElement(identifier)('???')
      |        PsiWhiteSpace('\n      ')
      |        ReferenceExpression: ???
      |          PsiElement(identifier)('???')
      |        PsiWhiteSpace('\n    ')
      |        IfStatement
      |          PsiElement(if)('if')
      |          PsiWhiteSpace(' ')
      |          BooleanLiteral
      |            PsiElement(true)('true')
      |          PsiWhiteSpace(' ')
      |          PsiElement(then)('then')
      |          PsiWhiteSpace(' ')
      |          IntegerLiteral
      |            PsiElement(integer)('42')
      |          PsiWhiteSpace('\n    ')
      |          PsiElement(else)('else')
      |          PsiWhiteSpace('\n        ')
      |          ReferenceExpression: ???
      |            PsiElement(identifier)('???')
      |        PsiWhiteSpace('\n        ')
      |        ReferenceExpression: ???
      |          PsiElement(identifier)('???')
      |        PsiWhiteSpace('\n\n    ')
      |        PsiComment(comment)('// for + do/yield/-')
      |        PsiWhiteSpace('\n    ')
      |        ForStatement
      |          PsiElement(for)('for')
      |          PsiWhiteSpace(' ')
      |          PsiElement(()('(')
      |          Enumerators
      |            Generator
      |              ReferencePattern: x
      |                PsiElement(identifier)('x')
      |              PsiWhiteSpace(' ')
      |              PsiElement(<-)('<-')
      |              PsiWhiteSpace(' ')
      |              InfixExpression
      |                IntegerLiteral
      |                  PsiElement(integer)('1')
      |                PsiWhiteSpace(' ')
      |                ReferenceExpression: to
      |                  PsiElement(identifier)('to')
      |                PsiWhiteSpace(' ')
      |                IntegerLiteral
      |                  PsiElement(integer)('3')
      |          PsiElement())(')')
      |          PsiWhiteSpace('\n      ')
      |          ReferenceExpression: ???
      |            PsiElement(identifier)('???')
      |        PsiWhiteSpace('\n      ')
      |        ReferenceExpression: ???
      |          PsiElement(identifier)('???')
      |        PsiWhiteSpace('\n    ')
      |        ForStatement
      |          PsiElement(for)('for')
      |          PsiWhiteSpace(' ')
      |          PsiElement(()('(')
      |          Enumerators
      |            Generator
      |              ReferencePattern: x
      |                PsiElement(identifier)('x')
      |              PsiWhiteSpace(' ')
      |              PsiElement(<-)('<-')
      |              PsiWhiteSpace(' ')
      |              InfixExpression
      |                IntegerLiteral
      |                  PsiElement(integer)('1')
      |                PsiWhiteSpace(' ')
      |                ReferenceExpression: to
      |                  PsiElement(identifier)('to')
      |                PsiWhiteSpace(' ')
      |                IntegerLiteral
      |                  PsiElement(integer)('3')
      |          PsiElement())(')')
      |          PsiWhiteSpace(' ')
      |          PsiElement(do)('do')
      |          PsiWhiteSpace('\n      ')
      |          ReferenceExpression: ???
      |            PsiElement(identifier)('???')
      |        PsiWhiteSpace('\n      ')
      |        ReferenceExpression: ???
      |          PsiElement(identifier)('???')
      |        PsiWhiteSpace('\n    ')
      |        ForStatement
      |          PsiElement(for)('for')
      |          PsiWhiteSpace(' ')
      |          PsiElement(()('(')
      |          Enumerators
      |            Generator
      |              ReferencePattern: x
      |                PsiElement(identifier)('x')
      |              PsiWhiteSpace(' ')
      |              PsiElement(<-)('<-')
      |              PsiWhiteSpace(' ')
      |              InfixExpression
      |                IntegerLiteral
      |                  PsiElement(integer)('1')
      |                PsiWhiteSpace(' ')
      |                ReferenceExpression: to
      |                  PsiElement(identifier)('to')
      |                PsiWhiteSpace(' ')
      |                IntegerLiteral
      |                  PsiElement(integer)('3')
      |          PsiElement())(')')
      |          PsiWhiteSpace(' ')
      |          PsiElement(yield)('yield')
      |          PsiWhiteSpace('\n      ')
      |          ReferenceExpression: ???
      |            PsiElement(identifier)('???')
      |        PsiWhiteSpace('\n      ')
      |        ReferenceExpression: ???
      |          PsiElement(identifier)('???')
      |        PsiWhiteSpace('\n\n    ')
      |        PsiComment(comment)('// while + do/-')
      |        PsiWhiteSpace('\n    ')
      |        WhileStatement
      |          PsiElement(while)('while')
      |          PsiWhiteSpace(' ')
      |          PsiElement(()('(')
      |          InfixExpression
      |            InfixExpression
      |              IntegerLiteral
      |                PsiElement(integer)('2')
      |              PsiWhiteSpace(' ')
      |              ReferenceExpression: *
      |                PsiElement(identifier)('*')
      |              PsiWhiteSpace(' ')
      |              IntegerLiteral
      |                PsiElement(integer)('2')
      |            PsiWhiteSpace(' ')
      |            ReferenceExpression: ==
      |              PsiElement(identifier)('==')
      |            PsiWhiteSpace(' ')
      |            IntegerLiteral
      |              PsiElement(integer)('5')
      |          PsiElement())(')')
      |          PsiWhiteSpace('\n      ')
      |          ReferenceExpression: ???
      |            PsiElement(identifier)('???')
      |        PsiWhiteSpace('\n      ')
      |        ReferenceExpression: ???
      |          PsiElement(identifier)('???')
      |        PsiWhiteSpace('\n    ')
      |        WhileStatement
      |          PsiElement(while)('while')
      |          PsiWhiteSpace(' ')
      |          ExpressionInParenthesis
      |            PsiElement(()('(')
      |            InfixExpression
      |              InfixExpression
      |                IntegerLiteral
      |                  PsiElement(integer)('2')
      |                PsiWhiteSpace(' ')
      |                ReferenceExpression: *
      |                  PsiElement(identifier)('*')
      |                PsiWhiteSpace(' ')
      |                IntegerLiteral
      |                  PsiElement(integer)('2')
      |              PsiWhiteSpace(' ')
      |              ReferenceExpression: ==
      |                PsiElement(identifier)('==')
      |              PsiWhiteSpace(' ')
      |              IntegerLiteral
      |                PsiElement(integer)('5')
      |            PsiElement())(')')
      |          PsiWhiteSpace(' ')
      |          PsiElement(do)('do')
      |          PsiWhiteSpace('\n      ')
      |          ReferenceExpression: ???
      |            PsiElement(identifier)('???')
      |        PsiWhiteSpace('\n      ')
      |        ReferenceExpression: ???
      |          PsiElement(identifier)('???')
      |        PsiWhiteSpace('\n\n    ')
      |        DocComment
      |          ScPsiDocToken(DOC_COMMENT_START)('/**')
      |          ScDocParagraph
      |            ScPsiDocToken(DOC_WHITESPACE)(' ')
      |            ScPsiDocToken(DOC_COMMENT_DATA)('try / finally (catch is handled in ')
      |            DocSyntaxElement 64
      |              ScPsiDocToken(DOC_LINK_TAG 64)('[[')
      |              CodeReferenceElement (scalaDoc): Scala3TestDataCaseClausesEditorStates
      |                PsiElement(identifier)('Scala3TestDataCaseClausesEditorStates')
      |              ScPsiDocToken(DOC_LINK_CLOSE_TAG 0)(']]')
      |            ScPsiDocToken(DOC_COMMENT_DATA)(')')
      |            ScPsiDocToken(DOC_WHITESPACE)(' ')
      |          ScPsiDocToken(DOC_COMMENT_END)('*/')
      |        PsiWhiteSpace('\n    ')
      |        TryStatement
      |          PsiElement(try)('try')
      |          PsiWhiteSpace('\n      ')
      |          ReferenceExpression: ???
      |            PsiElement(identifier)('???')
      |        PsiWhiteSpace('\n      ')
      |        ReferenceExpression: ???
      |          PsiElement(identifier)('???')
      |        PsiErrorElement:';' or newline expected
      |          <empty list>
      |        PsiWhiteSpace('\n    ')
      |        PsiElement(catch)('catch')
      |        PsiWhiteSpace(' ')
      |        BlockExpression
      |          PsiElement({)('{')
      |          PsiWhiteSpace('\n      ')
      |          CaseClauses
      |            CaseClause
      |              PsiElement(case)('case')
      |              PsiWhiteSpace(' ')
      |              WildcardPattern
      |                PsiElement(_)('_')
      |              PsiWhiteSpace(' ')
      |              PsiElement(=>)('=>')
      |              BlockOfExpressions
      |                <empty list>
      |          PsiWhiteSpace('\n    ')
      |          PsiElement(})('}')
      |        PsiWhiteSpace('\n\n    ')
      |        TryStatement
      |          PsiElement(try)('try')
      |          PsiWhiteSpace(' ')
      |          IntegerLiteral
      |            PsiElement(integer)('42')
      |          PsiWhiteSpace('\n    ')
      |          FinallyBlock
      |            PsiElement(finally)('finally')
      |            PsiWhiteSpace('\n      ')
      |            ReferenceExpression: ???
      |              PsiElement(identifier)('???')
      |        PsiWhiteSpace('\n      ')
      |        ReferenceExpression: ???
      |          PsiElement(identifier)('???')
      |        PsiWhiteSpace('\n\n    ')
      |        PsiComment(comment)('// throw')
      |        PsiWhiteSpace('\n    ')
      |        ThrowStatement
      |          PsiElement(throw)('throw')
      |          PsiWhiteSpace('\n      ')
      |          ReferenceExpression: ???
      |            PsiElement(identifier)('???')
      |        PsiWhiteSpace('\n      ')
      |        ReferenceExpression: ???
      |          PsiElement(identifier)('???')
      |        PsiWhiteSpace('\n')
      |        PsiElement(})('}')""".stripMargin
  )

  def testExtensions(): Unit = checkTree(
    """extension (x: String)
      |  def f1 = ???
      |
      |extension (x: String)
      |  def f2 = ???
      |  def f3 = ???""".stripMargin,
    """ScalaFile
      |  Extension on String
      |    PsiElement(extension)('extension')
      |    PsiWhiteSpace(' ')
      |    Parameters
      |      ParametersClause
      |        PsiElement(()('(')
      |        Parameter: x
      |          AnnotationsList
      |            <empty list>
      |          Modifiers
      |            <empty list>
      |          PsiElement(identifier)('x')
      |          PsiElement(:)(':')
      |          PsiWhiteSpace(' ')
      |          ParameterType
      |            SimpleType: String
      |              CodeReferenceElement: String
      |                PsiElement(identifier)('String')
      |        PsiElement())(')')
      |    ScExtensionBody
      |      <empty list>
      |  PsiWhiteSpace('\n  ')
      |  ScFunctionDefinition: f1
      |    AnnotationsList
      |      <empty list>
      |    Modifiers
      |      <empty list>
      |    PsiElement(def)('def')
      |    PsiWhiteSpace(' ')
      |    PsiElement(identifier)('f1')
      |    Parameters
      |      <empty list>
      |    PsiWhiteSpace(' ')
      |    PsiElement(=)('=')
      |    PsiWhiteSpace(' ')
      |    ReferenceExpression: ???
      |      PsiElement(identifier)('???')
      |  PsiWhiteSpace('\n\n')
      |  Extension on String
      |    PsiElement(extension)('extension')
      |    PsiWhiteSpace(' ')
      |    Parameters
      |      ParametersClause
      |        PsiElement(()('(')
      |        Parameter: x
      |          AnnotationsList
      |            <empty list>
      |          Modifiers
      |            <empty list>
      |          PsiElement(identifier)('x')
      |          PsiElement(:)(':')
      |          PsiWhiteSpace(' ')
      |          ParameterType
      |            SimpleType: String
      |              CodeReferenceElement: String
      |                PsiElement(identifier)('String')
      |        PsiElement())(')')
      |    ScExtensionBody
      |      <empty list>
      |  PsiWhiteSpace('\n  ')
      |  ScFunctionDefinition: f2
      |    AnnotationsList
      |      <empty list>
      |    Modifiers
      |      <empty list>
      |    PsiElement(def)('def')
      |    PsiWhiteSpace(' ')
      |    PsiElement(identifier)('f2')
      |    Parameters
      |      <empty list>
      |    PsiWhiteSpace(' ')
      |    PsiElement(=)('=')
      |    PsiWhiteSpace(' ')
      |    ReferenceExpression: ???
      |      PsiElement(identifier)('???')
      |  PsiWhiteSpace('\n  ')
      |  ScFunctionDefinition: f3
      |    AnnotationsList
      |      <empty list>
      |    Modifiers
      |      <empty list>
      |    PsiElement(def)('def')
      |    PsiWhiteSpace(' ')
      |    PsiElement(identifier)('f3')
      |    Parameters
      |      <empty list>
      |    PsiWhiteSpace(' ')
      |    PsiElement(=)('=')
      |    PsiWhiteSpace(' ')
      |    ReferenceExpression: ???
      |      PsiElement(identifier)('???')""".stripMargin,
  )

  def testExtensions_CorrectSingleLineExtension(): Unit = {
    val code = """extension (x: String) def f1 = ???"""
    checkParseErrors(code)
    checkTree(
      code.stripMargin,
      """ScalaFile
        |  Extension on String
        |    PsiElement(extension)('extension')
        |    PsiWhiteSpace(' ')
        |    Parameters
        |      ParametersClause
        |        PsiElement(()('(')
        |        Parameter: x
        |          AnnotationsList
        |            <empty list>
        |          Modifiers
        |            <empty list>
        |          PsiElement(identifier)('x')
        |          PsiElement(:)(':')
        |          PsiWhiteSpace(' ')
        |          ParameterType
        |            SimpleType: String
        |              CodeReferenceElement: String
        |                PsiElement(identifier)('String')
        |        PsiElement())(')')
        |    PsiWhiteSpace(' ')
        |    ScExtensionBody
        |      ScFunctionDefinition: f1
        |        AnnotationsList
        |          <empty list>
        |        Modifiers
        |          <empty list>
        |        PsiElement(def)('def')
        |        PsiWhiteSpace(' ')
        |        PsiElement(identifier)('f1')
        |        Parameters
        |          <empty list>
        |        PsiWhiteSpace(' ')
        |        PsiElement(=)('=')
        |        PsiWhiteSpace(' ')
        |        ReferenceExpression: ???
        |          PsiElement(identifier)('???')""".stripMargin,
    )
  }

  def testGivenWith(): Unit = checkTree(
    """given intOrd: Ord[Int] with
      |
      |given intOrd: Ord[Int] with
      |  def f1 = ???
      |  def f2 = ???
      |""".stripMargin,
    """ScalaFile
      |  ScGivenDefinition: intOrd
      |    AnnotationsList
      |      <empty list>
      |    Modifiers
      |      <empty list>
      |    PsiElement(given)('given')
      |    PsiWhiteSpace(' ')
      |    PsiElement(identifier)('intOrd')
      |    Parameters
      |      <empty list>
      |    PsiElement(:)(':')
      |    PsiWhiteSpace(' ')
      |    ExtendsBlock
      |      TemplateParents
      |        ConstructorInvocation
      |          ParametrizedType: Ord[Int]
      |            SimpleType: Ord
      |              CodeReferenceElement: Ord
      |                PsiElement(identifier)('Ord')
      |            TypeArgumentsList
      |              PsiElement([)('[')
      |              SimpleType: Int
      |                CodeReferenceElement: Int
      |                  PsiElement(identifier)('Int')
      |              PsiElement(])(']')
      |      PsiWhiteSpace(' ')
      |      PsiElement(with)('with')
      |      PsiErrorElement:'{' expected
      |        <empty list>
      |  PsiWhiteSpace('\n\n')
      |  ScGivenDefinition: intOrd
      |    AnnotationsList
      |      <empty list>
      |    Modifiers
      |      <empty list>
      |    PsiElement(given)('given')
      |    PsiWhiteSpace(' ')
      |    PsiElement(identifier)('intOrd')
      |    Parameters
      |      <empty list>
      |    PsiElement(:)(':')
      |    PsiWhiteSpace(' ')
      |    ExtendsBlock
      |      TemplateParents
      |        ConstructorInvocation
      |          ParametrizedType: Ord[Int]
      |            SimpleType: Ord
      |              CodeReferenceElement: Ord
      |                PsiElement(identifier)('Ord')
      |            TypeArgumentsList
      |              PsiElement([)('[')
      |              SimpleType: Int
      |                CodeReferenceElement: Int
      |                  PsiElement(identifier)('Int')
      |              PsiElement(])(']')
      |      PsiWhiteSpace(' ')
      |      PsiElement(with)('with')
      |      PsiErrorElement:'{' expected
      |        <empty list>
      |  PsiWhiteSpace('\n  ')
      |  ScFunctionDefinition: f1
      |    AnnotationsList
      |      <empty list>
      |    Modifiers
      |      <empty list>
      |    PsiElement(def)('def')
      |    PsiWhiteSpace(' ')
      |    PsiElement(identifier)('f1')
      |    Parameters
      |      <empty list>
      |    PsiWhiteSpace(' ')
      |    PsiElement(=)('=')
      |    PsiWhiteSpace(' ')
      |    ReferenceExpression: ???
      |      PsiElement(identifier)('???')
      |  PsiWhiteSpace('\n  ')
      |  ScFunctionDefinition: f2
      |    AnnotationsList
      |      <empty list>
      |    Modifiers
      |      <empty list>
      |    PsiElement(def)('def')
      |    PsiWhiteSpace(' ')
      |    PsiElement(identifier)('f2')
      |    Parameters
      |      <empty list>
      |    PsiWhiteSpace(' ')
      |    PsiElement(=)('=')
      |    PsiWhiteSpace(' ')
      |    ReferenceExpression: ???
      |      PsiElement(identifier)('???')
      |  PsiWhiteSpace('\n')""".stripMargin,
  )

  def testCaseClauses(): Unit = checkTree(
    """42 match
      |  case 42 => println(1)
      |  case _ => println(2)
      |""".stripMargin,
    """ScalaFile
      |  MatchStatement
      |    IntegerLiteral
      |      PsiElement(integer)('42')
      |    PsiWhiteSpace(' ')
      |    PsiElement(match)('match')
      |    PsiErrorElement:Case clauses expected
      |      <empty list>
      |  PsiWhiteSpace('\n  ')
      |  PsiElement(case)('case')
      |  PsiWhiteSpace(' ')
      |  IntegerLiteral
      |    PsiElement(integer)('42')
      |  PsiErrorElement:';' or newline expected
      |    <empty list>
      |  PsiWhiteSpace(' ')
      |  PsiElement(=>)('=>')
      |  PsiWhiteSpace(' ')
      |  MethodCall
      |    ReferenceExpression: println
      |      PsiElement(identifier)('println')
      |    ArgumentList
      |      PsiElement(()('(')
      |      IntegerLiteral
      |        PsiElement(integer)('1')
      |      PsiElement())(')')
      |  PsiErrorElement:';' or newline expected
      |    <empty list>
      |  PsiWhiteSpace('\n  ')
      |  PsiElement(case)('case')
      |  PsiWhiteSpace(' ')
      |  FunctionExpression
      |    Parameters
      |      ParametersClause
      |        Parameter: _
      |          PsiElement(_)('_')
      |    PsiWhiteSpace(' ')
      |    PsiElement(=>)('=>')
      |    PsiWhiteSpace(' ')
      |    MethodCall
      |      ReferenceExpression: println
      |        PsiElement(identifier)('println')
      |      ArgumentList
      |        PsiElement(()('(')
      |        IntegerLiteral
      |          PsiElement(integer)('2')
      |        PsiElement())(')')
      |  PsiWhiteSpace('\n')""".stripMargin
  )
}
