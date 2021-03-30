package org.jetbrains.plugins.scala.lang.parser.scala3

class FunDefTest extends SimpleScala3ParserTestBase {

  def test_fun_def_not_indented(): Unit = checkTree(
    """def foo=
      |println("foo 1")
      |println("foo 2")
      |
      |class A {
      |  def foo=
      |  println("foo 1")
      |  println("foo 2")
      |}""".stripMargin,
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
      |    PsiElement(=)('=')
      |    PsiWhiteSpace('\n')
      |    MethodCall
      |      ReferenceExpression: println
      |        PsiElement(identifier)('println')
      |      ArgumentList
      |        PsiElement(()('(')
      |        StringLiteral
      |          PsiElement(string content)('"foo 1"')
      |        PsiElement())(')')
      |  PsiWhiteSpace('\n')
      |  MethodCall
      |    ReferenceExpression: println
      |      PsiElement(identifier)('println')
      |    ArgumentList
      |      PsiElement(()('(')
      |      StringLiteral
      |        PsiElement(string content)('"foo 2"')
      |      PsiElement())(')')
      |  PsiWhiteSpace('\n\n')
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
      |          PsiElement(=)('=')
      |          PsiWhiteSpace('\n  ')
      |          MethodCall
      |            ReferenceExpression: println
      |              PsiElement(identifier)('println')
      |            ArgumentList
      |              PsiElement(()('(')
      |              StringLiteral
      |                PsiElement(string content)('"foo 1"')
      |              PsiElement())(')')
      |        PsiWhiteSpace('\n  ')
      |        MethodCall
      |          ReferenceExpression: println
      |            PsiElement(identifier)('println')
      |          ArgumentList
      |            PsiElement(()('(')
      |            StringLiteral
      |              PsiElement(string content)('"foo 2"')
      |            PsiElement())(')')
      |        PsiWhiteSpace('\n')
      |        PsiElement(})('}')""".stripMargin
  )

  def test_fun_def_not_indented_unindented(): Unit = checkTree(
    """class A {
      |  def foo=
      | println("foo 1")
      |  println("foo 2")
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
      |          PsiElement(=)('=')
      |          PsiErrorElement:Line is indented too far to the left
      |            <empty list>
      |          PsiWhiteSpace('\n ')
      |          MethodCall
      |            ReferenceExpression: println
      |              PsiElement(identifier)('println')
      |            ArgumentList
      |              PsiElement(()('(')
      |              StringLiteral
      |                PsiElement(string content)('"foo 1"')
      |              PsiElement())(')')
      |        PsiWhiteSpace('\n  ')
      |        MethodCall
      |          ReferenceExpression: println
      |            PsiElement(identifier)('println')
      |          ArgumentList
      |            PsiElement(()('(')
      |            StringLiteral
      |              PsiElement(string content)('"foo 2"')
      |            PsiElement())(')')
      |        PsiWhiteSpace('\n')
      |        PsiElement(})('}')""".stripMargin
  )
}