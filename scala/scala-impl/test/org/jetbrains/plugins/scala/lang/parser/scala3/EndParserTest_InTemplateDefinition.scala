package org.jetbrains.plugins.scala.lang.parser.scala3

import org.jetbrains.plugins.scala.AssertionMatchers
import org.jetbrains.plugins.scala.util.PsiSelectionUtil

class EndParserTest_InTemplateDefinition extends SimpleScala3ParserTestBase with PsiSelectionUtil with AssertionMatchers {

  def test_end_class_empty_template_body(): Unit = checkTree(
    """class A
      |end A
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
      |    ExtendsBlock
      |      <empty list>
      |    PsiWhiteSpace('\n')
      |    End: A
      |      PsiElement(end)('end')
      |      PsiWhiteSpace(' ')
      |      PsiElement(identifier)('A')
      |  PsiWhiteSpace('\n')""".stripMargin
  )

  def test_end_class_empty_template_body_with_colon(): Unit = checkTree(
    """class A:
      |end A
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
      |    ExtendsBlock
      |      ScTemplateBody
      |        PsiElement(:)(':')
      |        PsiWhiteSpace('\n')
      |        End: A
      |          PsiElement(end)('end')
      |          PsiWhiteSpace(' ')
      |          PsiElement(identifier)('A')
      |  PsiWhiteSpace('\n')""".stripMargin
  )

  def test_end_class_non_empty_template_body_with_colon(): Unit = checkTree(
    """class A:
      |  def foo = ???
      |end A
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
      |          PsiWhiteSpace(' ')
      |          ReferenceExpression: ???
      |            PsiElement(identifier)('???')
      |        PsiWhiteSpace('\n')
      |        End: A
      |          PsiElement(end)('end')
      |          PsiWhiteSpace(' ')
      |          PsiElement(identifier)('A')
      |  PsiWhiteSpace('\n')""".stripMargin
  )

  def test_end_object_empty_template_body(): Unit = checkTree(
    """object A
      |end A
      |""".stripMargin,
    """ScalaFile
      |  ScObject: A
      |    AnnotationsList
      |      <empty list>
      |    Modifiers
      |      <empty list>
      |    PsiElement(object)('object')
      |    PsiWhiteSpace(' ')
      |    PsiElement(identifier)('A')
      |    ExtendsBlock
      |      <empty list>
      |    PsiWhiteSpace('\n')
      |    End: A
      |      PsiElement(end)('end')
      |      PsiWhiteSpace(' ')
      |      PsiElement(identifier)('A')
      |  PsiWhiteSpace('\n')""".stripMargin
  )

  def test_end_object_empty_template_body_with_colon(): Unit = checkTree(
    """object A:
      |end A
      |""".stripMargin,
    """ScalaFile
      |  ScObject: A
      |    AnnotationsList
      |      <empty list>
      |    Modifiers
      |      <empty list>
      |    PsiElement(object)('object')
      |    PsiWhiteSpace(' ')
      |    PsiElement(identifier)('A')
      |    ExtendsBlock
      |      ScTemplateBody
      |        PsiElement(:)(':')
      |        PsiWhiteSpace('\n')
      |        End: A
      |          PsiElement(end)('end')
      |          PsiWhiteSpace(' ')
      |          PsiElement(identifier)('A')
      |  PsiWhiteSpace('\n')""".stripMargin
  )

  def test_end_object_non_empty_template_body_with_colon(): Unit = checkTree(
    """object A:
      |  def foo = ???
      |end A
      |""".stripMargin,
    """ScalaFile
      |  ScObject: A
      |    AnnotationsList
      |      <empty list>
      |    Modifiers
      |      <empty list>
      |    PsiElement(object)('object')
      |    PsiWhiteSpace(' ')
      |    PsiElement(identifier)('A')
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
      |          PsiWhiteSpace(' ')
      |          ReferenceExpression: ???
      |            PsiElement(identifier)('???')
      |        PsiWhiteSpace('\n')
      |        End: A
      |          PsiElement(end)('end')
      |          PsiWhiteSpace(' ')
      |          PsiElement(identifier)('A')
      |  PsiWhiteSpace('\n')""".stripMargin
  )

  def test_end_enum_empty_template_body_with_colon(): Unit = checkTree(
    """enum A:
      |end A
      |""".stripMargin,
    """ScalaFile
      |  ScEnum: A
      |    AnnotationsList
      |      <empty list>
      |    Modifiers
      |      <empty list>
      |    PsiElement(enum)('enum')
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
      |        PsiWhiteSpace('\n')
      |        End: A
      |          PsiElement(end)('end')
      |          PsiWhiteSpace(' ')
      |          PsiElement(identifier)('A')
      |  PsiWhiteSpace('\n')""".stripMargin
  )

  def test_end_enum_non_empty_template_body_with_colon(): Unit = checkTree(
    """enum A:
      |  case X
      |end A
      |""".stripMargin,
    """ScalaFile
      |  ScEnum: A
      |    AnnotationsList
      |      <empty list>
      |    Modifiers
      |      <empty list>
      |    PsiElement(enum)('enum')
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
      |        PsiWhiteSpace('\n  ')
      |        ScEnumCases: X
      |          AnnotationsList
      |            <empty list>
      |          Modifiers
      |            <empty list>
      |          PsiElement(case)('case')
      |          PsiWhiteSpace(' ')
      |          ScEnumCase: X
      |            PsiElement(identifier)('X')
      |            ExtendsBlock
      |              <empty list>
      |        PsiWhiteSpace('\n')
      |        End: A
      |          PsiElement(end)('end')
      |          PsiWhiteSpace(' ')
      |          PsiElement(identifier)('A')
      |  PsiWhiteSpace('\n')""".stripMargin
  )

  def test_end_given_with_empty_template_body(): Unit = checkTree(
    """given Conversion[Int, String] with
      |end given""".stripMargin,
    """ScalaFile
      |  ScGivenDefinition: given_Conversion_Int_String
      |    AnnotationsList
      |      <empty list>
      |    Modifiers
      |      <empty list>
      |    PsiElement(given)('given')
      |    PsiWhiteSpace(' ')
      |    ExtendsBlock
      |      TemplateParents
      |        ConstructorInvocation
      |          ParametrizedType: Conversion[Int, String]
      |            SimpleType: Conversion
      |              CodeReferenceElement: Conversion
      |                PsiElement(identifier)('Conversion')
      |            TypeArgumentsList
      |              PsiElement([)('[')
      |              SimpleType: Int
      |                CodeReferenceElement: Int
      |                  PsiElement(identifier)('Int')
      |              PsiElement(,)(',')
      |              PsiWhiteSpace(' ')
      |              SimpleType: String
      |                CodeReferenceElement: String
      |                  PsiElement(identifier)('String')
      |              PsiElement(])(']')
      |      PsiWhiteSpace(' ')
      |      PsiElement(with)('with')
      |      PsiWhiteSpace('\n')
      |      ScTemplateBody
      |        End: given
      |          PsiElement(end)('end')
      |          PsiWhiteSpace(' ')
      |          PsiElement(given)('given')""".stripMargin
  )

  def test_end_given_with_non_empty_template_body(): Unit = checkTree(
    """given Conversion[Int, String] with
      |  def foo = ???
      |end given""".stripMargin,
    """ScalaFile
      |  ScGivenDefinition: given_Conversion_Int_String
      |    AnnotationsList
      |      <empty list>
      |    Modifiers
      |      <empty list>
      |    PsiElement(given)('given')
      |    PsiWhiteSpace(' ')
      |    ExtendsBlock
      |      TemplateParents
      |        ConstructorInvocation
      |          ParametrizedType: Conversion[Int, String]
      |            SimpleType: Conversion
      |              CodeReferenceElement: Conversion
      |                PsiElement(identifier)('Conversion')
      |            TypeArgumentsList
      |              PsiElement([)('[')
      |              SimpleType: Int
      |                CodeReferenceElement: Int
      |                  PsiElement(identifier)('Int')
      |              PsiElement(,)(',')
      |              PsiWhiteSpace(' ')
      |              SimpleType: String
      |                CodeReferenceElement: String
      |                  PsiElement(identifier)('String')
      |              PsiElement(])(']')
      |      PsiWhiteSpace(' ')
      |      PsiElement(with)('with')
      |      PsiWhiteSpace('\n  ')
      |      ScTemplateBody
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
      |          PsiWhiteSpace(' ')
      |          ReferenceExpression: ???
      |            PsiElement(identifier)('???')
      |        PsiWhiteSpace('\n')
      |        End: given
      |          PsiElement(end)('end')
      |          PsiWhiteSpace(' ')
      |          PsiElement(given)('given')""".stripMargin
  )
}
