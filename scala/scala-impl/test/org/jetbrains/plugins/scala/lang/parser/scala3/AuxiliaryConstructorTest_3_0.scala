package org.jetbrains.plugins.scala.lang.parser.scala3

import org.jetbrains.plugins.scala.lang.parser.AuxiliaryConstructorTestBase

class AuxiliaryConstructorTest_3_0 extends AuxiliaryConstructorTestBase with SimpleScala3ParserTestBase {

  def test_correct_block_with_multiple_expressions(): Unit = checkTree(
    """class A {
      |  def this(x: Short) = {
      |    this()
      |    println(1)
      |  }
      |}
      |""".stripMargin,
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
      |        ScFunctionDefinition: this
      |          AnnotationsList
      |            <empty list>
      |          Modifiers
      |            <empty list>
      |          PsiElement(def)('def')
      |          PsiWhiteSpace(' ')
      |          PsiElement(this)('this')
      |          Parameters
      |            ParametersClause
      |              PsiElement(()('(')
      |              Parameter: x
      |                AnnotationsList
      |                  <empty list>
      |                Modifiers
      |                  <empty list>
      |                PsiElement(identifier)('x')
      |                PsiElement(:)(':')
      |                PsiWhiteSpace(' ')
      |                ParameterType
      |                  SimpleType: Short
      |                    CodeReferenceElement: Short
      |                      PsiElement(identifier)('Short')
      |              PsiElement())(')')
      |          PsiWhiteSpace(' ')
      |          PsiElement(=)('=')
      |          PsiWhiteSpace(' ')
      |          ConstructorBlock
      |            PsiElement({)('{')
      |            PsiWhiteSpace('\n    ')
      |            SelfInvocation
      |              PsiElement(this)('this')
      |              ArgumentList
      |                PsiElement(()('(')
      |                PsiElement())(')')
      |            PsiWhiteSpace('\n    ')
      |            MethodCall
      |              ReferenceExpression: println
      |                PsiElement(identifier)('println')
      |              ArgumentList
      |                PsiElement(()('(')
      |                IntegerLiteral
      |                  PsiElement(integer)('1')
      |                PsiElement())(')')
      |            PsiWhiteSpace('\n  ')
      |            PsiElement(})('}')
      |        PsiWhiteSpace('\n')
      |        PsiElement(})('}')
      |  PsiWhiteSpace('\n')""".stripMargin
  )

  def test_correct_block_with_multiple_expressions_braceless_syntax(): Unit = checkTree(
    """class A {
      |  def this(x: String) =
      |    this()
      |    println(2)
      |}
      |""".stripMargin,
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
      |        ScFunctionDefinition: this
      |          AnnotationsList
      |            <empty list>
      |          Modifiers
      |            <empty list>
      |          PsiElement(def)('def')
      |          PsiWhiteSpace(' ')
      |          PsiElement(this)('this')
      |          Parameters
      |            ParametersClause
      |              PsiElement(()('(')
      |              Parameter: x
      |                AnnotationsList
      |                  <empty list>
      |                Modifiers
      |                  <empty list>
      |                PsiElement(identifier)('x')
      |                PsiElement(:)(':')
      |                PsiWhiteSpace(' ')
      |                ParameterType
      |                  SimpleType: String
      |                    CodeReferenceElement: String
      |                      PsiElement(identifier)('String')
      |              PsiElement())(')')
      |          PsiWhiteSpace(' ')
      |          PsiElement(=)('=')
      |          ConstructorBlock
      |            PsiWhiteSpace('\n    ')
      |            SelfInvocation
      |              PsiElement(this)('this')
      |              ArgumentList
      |                PsiElement(()('(')
      |                PsiElement())(')')
      |            PsiWhiteSpace('\n    ')
      |            MethodCall
      |              ReferenceExpression: println
      |                PsiElement(identifier)('println')
      |              ArgumentList
      |                PsiElement(()('(')
      |                IntegerLiteral
      |                  PsiElement(integer)('2')
      |                PsiElement())(')')
      |        PsiWhiteSpace('\n')
      |        PsiElement(})('}')
      |  PsiWhiteSpace('\n')""".stripMargin
  )

  override def test_bad_missing_expr(): Unit = checkTree(
    """
      |class Test {
      |  def this() =
      |}
      |""".stripMargin,
    """ScalaFile
      |  PsiWhiteSpace('\n')
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
      |    PsiWhiteSpace(' ')
      |    ExtendsBlock
      |      ScTemplateBody
      |        PsiElement({)('{')
      |        PsiWhiteSpace('\n  ')
      |        ScFunctionDefinition: this
      |          AnnotationsList
      |            <empty list>
      |          Modifiers
      |            <empty list>
      |          PsiElement(def)('def')
      |          PsiWhiteSpace(' ')
      |          PsiElement(this)('this')
      |          Parameters
      |            ParametersClause
      |              PsiElement(()('(')
      |              PsiElement())(')')
      |          PsiWhiteSpace(' ')
      |          PsiElement(=)('=')
      |          PsiErrorElement:Wrong constructor expression
      |            <empty list>
      |        PsiWhiteSpace('\n')
      |        PsiElement(})('}')
      |  PsiWhiteSpace('\n')""".stripMargin
  )

  override def test_bad_statement_instead_of_self_invocation(): Unit = checkTree(
    bad_statement_instead_of_self_invocation_code,
    """ScalaFile
      |  ScClass: D
      |    AnnotationsList
      |      <empty list>
      |    Modifiers
      |      <empty list>
      |    PsiElement(class)('class')
      |    PsiWhiteSpace(' ')
      |    PsiElement(identifier)('D')
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
      |      ScTemplateBody
      |        PsiElement({)('{')
      |        PsiWhiteSpace('\n  ')
      |        ScFunctionDefinition: this
      |          AnnotationsList
      |            <empty list>
      |          Modifiers
      |            <empty list>
      |          PsiElement(def)('def')
      |          PsiWhiteSpace(' ')
      |          PsiElement(this)('this')
      |          Parameters
      |            ParametersClause
      |              PsiElement(()('(')
      |              PsiElement())(')')
      |          PsiWhiteSpace(' ')
      |          PsiElement(=)('=')
      |          ConstructorBlock
      |            PsiWhiteSpace('\n    ')
      |            ScPatternDefinition: x
      |              AnnotationsList
      |                <empty list>
      |              Modifiers
      |                <empty list>
      |              PsiElement(val)('val')
      |              PsiWhiteSpace(' ')
      |              ListOfPatterns
      |                ReferencePattern: x
      |                  PsiElement(identifier)('x')
      |              PsiWhiteSpace(' ')
      |              PsiElement(=)('=')
      |              PsiWhiteSpace(' ')
      |              IntegerLiteral
      |                PsiElement(integer)('42')
      |        PsiWhiteSpace('\n')
      |        PsiElement(})('}')
      |  PsiWhiteSpace('\n')""".stripMargin
  )

  override   def test_correct_single_expression_no_braces_2_unindented_body(): Unit = checkTree(
    """
      |class Test {
      |  def this() =
      | this()
      |}
      |""".stripMargin,
    """ScalaFile
      |  PsiWhiteSpace('\n')
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
      |    PsiWhiteSpace(' ')
      |    ExtendsBlock
      |      ScTemplateBody
      |        PsiElement({)('{')
      |        PsiWhiteSpace('\n  ')
      |        ScFunctionDefinition: this
      |          AnnotationsList
      |            <empty list>
      |          Modifiers
      |            <empty list>
      |          PsiElement(def)('def')
      |          PsiWhiteSpace(' ')
      |          PsiElement(this)('this')
      |          Parameters
      |            ParametersClause
      |              PsiElement(()('(')
      |              PsiElement())(')')
      |          PsiWhiteSpace(' ')
      |          PsiElement(=)('=')
      |          PsiErrorElement:Line is indented too far to the left
      |            <empty list>
      |          PsiWhiteSpace('\n ')
      |          SelfInvocation
      |            PsiElement(this)('this')
      |            ArgumentList
      |              PsiElement(()('(')
      |              PsiElement())(')')
      |        PsiWhiteSpace('\n')
      |        PsiElement(})('}')
      |  PsiWhiteSpace('\n')""".stripMargin
  )
}
