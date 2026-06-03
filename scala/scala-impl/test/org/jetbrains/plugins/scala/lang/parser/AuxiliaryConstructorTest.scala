package org.jetbrains.plugins.scala.lang.parser

// #SCL-18521
class AuxiliaryConstructorTest extends AuxiliaryConstructorTestBase

abstract class AuxiliaryConstructorTestBase extends SimpleScalaParserTestBase {

  def test_correct_single_expression_no_braces_0(): Unit = checkTree(
    """
      |class Test {
      |  def this() = this()
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
      |          PsiWhiteSpace(' ')
      |          SelfInvocation
      |            PsiElement(this)('this')
      |            ArgumentList
      |              PsiElement(()('(')
      |              PsiElement())(')')
      |        PsiWhiteSpace('\n')
      |        PsiElement(})('}')
      |  PsiWhiteSpace('\n')""".stripMargin
  )

  def test_correct_single_expression_no_braces_1(): Unit = checkTree(
    """
      |class Test {
      |  def this() =
      |    this()
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
      |          PsiWhiteSpace('\n    ')
      |          SelfInvocation
      |            PsiElement(this)('this')
      |            ArgumentList
      |              PsiElement(()('(')
      |              PsiElement())(')')
      |        PsiWhiteSpace('\n')
      |        PsiElement(})('}')
      |  PsiWhiteSpace('\n')""".stripMargin
  )

  def test_correct_single_expression_no_braces_2_unindented_body(): Unit = checkTree(
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

  def test_correct_block(): Unit = checkTree(
    """
      |class Test {
      |  def this() = {
      |    this()
      |  }
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
      |          PsiWhiteSpace(' ')
      |          ConstructorBlock
      |            PsiElement({)('{')
      |            PsiWhiteSpace('\n    ')
      |            SelfInvocation
      |              PsiElement(this)('this')
      |              ArgumentList
      |                PsiElement(()('(')
      |                PsiElement())(')')
      |            PsiWhiteSpace('\n  ')
      |            PsiElement(})('}')
      |        PsiWhiteSpace('\n')
      |        PsiElement(})('}')
      |  PsiWhiteSpace('\n')
      |""".stripMargin
  )

  def test_correct_block_procedure_syntax(): Unit = checkTree(
    """
      |class Test {
      |  def this() {
      |    this()
      |  }
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
      |          ConstructorBlock
      |            PsiElement({)('{')
      |            PsiWhiteSpace('\n    ')
      |            SelfInvocation
      |              PsiElement(this)('this')
      |              ArgumentList
      |                PsiElement(()('(')
      |                PsiElement())(')')
      |            PsiWhiteSpace('\n  ')
      |            PsiElement(})('}')
      |        PsiWhiteSpace('\n')
      |        PsiElement(})('}')
      |  PsiWhiteSpace('\n')
      |""".stripMargin
  )

  def test_decl(): Unit = checkTree(
    """
      |class Test {
      |  def this()
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
      |          PsiErrorElement:Auxiliary constructor definition expected
      |            <empty list>
      |        PsiWhiteSpace('\n')
      |        PsiElement(})('}')
      |  PsiWhiteSpace('\n')
      |""".stripMargin
  )

  protected val test_bad_missing_expr_code =
    """
      |class Test {
      |  def this() =
      |}
      |""".stripMargin
  def test_bad_missing_expr(): Unit = checkTree(
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
      |  PsiWhiteSpace('\n')
      |""".stripMargin
  )

  def test_bad_expr_instead_of_self_invocation(): Unit = checkTree(
    """
      |class Test {
      |  def this() = ???
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
      |          PsiWhiteSpace(' ')
      |          ReferenceExpression: ???
      |            PsiElement(identifier)('???')
      |        PsiWhiteSpace('\n')
      |        PsiElement(})('}')
      |  PsiWhiteSpace('\n')""".stripMargin
  )

  protected val bad_statement_instead_of_self_invocation_code =
    """class D(x: Int) {
      |  def this() =
      |    val x = 42
      |}
      |""".stripMargin
  def test_bad_statement_instead_of_self_invocation(): Unit = checkTree(
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
      |          PsiErrorElement:Wrong constructor expression
      |            <empty list>
      |        PsiWhiteSpace('\n    ')
      |        ScPatternDefinition: x
      |          AnnotationsList
      |            <empty list>
      |          Modifiers
      |            <empty list>
      |          PsiElement(val)('val')
      |          PsiWhiteSpace(' ')
      |          ListOfPatterns
      |            ReferencePattern: x
      |              PsiElement(identifier)('x')
      |          PsiWhiteSpace(' ')
      |          PsiElement(=)('=')
      |          PsiWhiteSpace(' ')
      |          IntegerLiteral
      |            PsiElement(integer)('42')
      |        PsiWhiteSpace('\n')
      |        PsiElement(})('}')
      |  PsiWhiteSpace('\n')""".stripMargin
  )

  def test_bad_statement_instead_of_self_invocation_with_braces(): Unit = checkTree(
    """class D(x: Int) {
      |  def this() = {
      |    val x = 42
      |  }
      |}
      |""".stripMargin,
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
      |          PsiWhiteSpace(' ')
      |          ConstructorBlock
      |            PsiElement({)('{')
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
      |            PsiWhiteSpace('\n  ')
      |            PsiElement(})('}')
      |        PsiWhiteSpace('\n')
      |        PsiElement(})('}')
      |  PsiWhiteSpace('\n')""".stripMargin
  )

  def test_bad_type_annotation(): Unit = checkTree(
    """
      |class Test {
      |  def this(): Int = this()
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
      |          PsiErrorElement:Auxiliary constructor may not have a type annotation
      |            PsiElement(:)(':')
      |            PsiWhiteSpace(' ')
      |            SimpleType: Int
      |              CodeReferenceElement: Int
      |                PsiElement(identifier)('Int')
      |          PsiWhiteSpace(' ')
      |          PsiElement(=)('=')
      |          PsiWhiteSpace(' ')
      |          SelfInvocation
      |            PsiElement(this)('this')
      |            ArgumentList
      |              PsiElement(()('(')
      |              PsiElement())(')')
      |        PsiWhiteSpace('\n')
      |        PsiElement(})('}')
      |  PsiWhiteSpace('\n')""".stripMargin
  )

  def test_bad_block_without_self_invocation_procedure_syntax(): Unit = checkTree(
    """
      |class Test {
      |  def this() {
      |    ???
      |  }
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
      |          ConstructorBlock
      |            PsiElement({)('{')
      |            PsiWhiteSpace('\n    ')
      |            ReferenceExpression: ???
      |              PsiElement(identifier)('???')
      |            PsiWhiteSpace('\n  ')
      |            PsiElement(})('}')
      |        PsiWhiteSpace('\n')
      |        PsiElement(})('}')
      |  PsiWhiteSpace('\n')
      |""".stripMargin
  )

  def test_bad_empty_block(): Unit = checkTree(
    """
      |class Test {
      |  def this() = {}
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
      |          PsiWhiteSpace(' ')
      |          ConstructorBlock
      |            PsiElement({)('{')
      |            PsiElement(})('}')
      |        PsiWhiteSpace('\n')
      |        PsiElement(})('}')
      |  PsiWhiteSpace('\n')
      |""".stripMargin
  )

  def test_bad_empty_block_procedure_syntax(): Unit = checkTree(
    """
      |class Test {
      |  def this() {}
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
      |          ConstructorBlock
      |            PsiElement({)('{')
      |            PsiElement(})('}')
      |        PsiWhiteSpace('\n')
      |        PsiElement(})('}')
      |  PsiWhiteSpace('\n')
      |""".stripMargin
  )
}
