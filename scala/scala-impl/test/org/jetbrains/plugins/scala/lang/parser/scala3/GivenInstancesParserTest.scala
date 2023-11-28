package org.jetbrains.plugins.scala.lang.parser.scala3

class GivenInstancesParserTest extends SimpleScala3ParserTestBase {

  def test_indentation(): Unit = checkTree(
    """
      |given Test with
      | def blub = 3
      |end given
      |""".stripMargin,
    """ScalaFile
      |  PsiWhiteSpace('\n')
      |  ScGivenDefinition: given_Test
      |    AnnotationsList
      |      <empty list>
      |    Modifiers
      |      <empty list>
      |    PsiElement(given)('given')
      |    PsiWhiteSpace(' ')
      |    ExtendsBlock
      |      TemplateParents
      |        ConstructorInvocation
      |          SimpleType: Test
      |            CodeReferenceElement: Test
      |              PsiElement(identifier)('Test')
      |      PsiWhiteSpace(' ')
      |      PsiElement(with)('with')
      |      PsiWhiteSpace('\n ')
      |      ScTemplateBody
      |        ScFunctionDefinition: blub
      |          AnnotationsList
      |            <empty list>
      |          Modifiers
      |            <empty list>
      |          PsiElement(def)('def')
      |          PsiWhiteSpace(' ')
      |          PsiElement(identifier)('blub')
      |          Parameters
      |            <empty list>
      |          PsiWhiteSpace(' ')
      |          PsiElement(=)('=')
      |          PsiWhiteSpace(' ')
      |          IntegerLiteral
      |            PsiElement(integer)('3')
      |        PsiWhiteSpace('\n')
      |        End: given
      |          PsiElement(end)('end')
      |          PsiWhiteSpace(' ')
      |          PsiElement(given)('given')
      |  PsiWhiteSpace('\n')""".stripMargin
  )

  def test_indentation_1(): Unit = checkTree(
    """
      |given Test with
      | println(42)
      | def blub = 3
      |end given
      |""".stripMargin,
    """ScalaFile
      |  PsiWhiteSpace('\n')
      |  ScGivenDefinition: given_Test
      |    AnnotationsList
      |      <empty list>
      |    Modifiers
      |      <empty list>
      |    PsiElement(given)('given')
      |    PsiWhiteSpace(' ')
      |    ExtendsBlock
      |      TemplateParents
      |        ConstructorInvocation
      |          SimpleType: Test
      |            CodeReferenceElement: Test
      |              PsiElement(identifier)('Test')
      |      PsiWhiteSpace(' ')
      |      PsiElement(with)('with')
      |      PsiWhiteSpace('\n ')
      |      ScTemplateBody
      |        MethodCall
      |          ReferenceExpression: println
      |            PsiElement(identifier)('println')
      |          ArgumentList
      |            PsiElement(()('(')
      |            IntegerLiteral
      |              PsiElement(integer)('42')
      |            PsiElement())(')')
      |        PsiWhiteSpace('\n ')
      |        ScFunctionDefinition: blub
      |          AnnotationsList
      |            <empty list>
      |          Modifiers
      |            <empty list>
      |          PsiElement(def)('def')
      |          PsiWhiteSpace(' ')
      |          PsiElement(identifier)('blub')
      |          Parameters
      |            <empty list>
      |          PsiWhiteSpace(' ')
      |          PsiElement(=)('=')
      |          PsiWhiteSpace(' ')
      |          IntegerLiteral
      |            PsiElement(integer)('3')
      |        PsiWhiteSpace('\n')
      |        End: given
      |          PsiElement(end)('end')
      |          PsiWhiteSpace(' ')
      |          PsiElement(given)('given')
      |  PsiWhiteSpace('\n')""".stripMargin
  )

  // can occur during typing, unindented expressions shouldn't be parsed as template body statements
  def test_indentation_incomplete_body_followed_by_unindented_expressions(): Unit = checkTree(
    """given Test with
      |println(1)
      |println(2)
      |""".stripMargin,
    """ScalaFile
      |  ScGivenDefinition: given_Test
      |    AnnotationsList
      |      <empty list>
      |    Modifiers
      |      <empty list>
      |    PsiElement(given)('given')
      |    PsiWhiteSpace(' ')
      |    ExtendsBlock
      |      TemplateParents
      |        ConstructorInvocation
      |          SimpleType: Test
      |            CodeReferenceElement: Test
      |              PsiElement(identifier)('Test')
      |      PsiWhiteSpace(' ')
      |      PsiElement(with)('with')
      |      ScTemplateBody
      |        <empty list>
      |  PsiWhiteSpace('\n')
      |  MethodCall
      |    ReferenceExpression: println
      |      PsiElement(identifier)('println')
      |    ArgumentList
      |      PsiElement(()('(')
      |      IntegerLiteral
      |        PsiElement(integer)('1')
      |      PsiElement())(')')
      |  PsiWhiteSpace('\n')
      |  MethodCall
      |    ReferenceExpression: println
      |      PsiElement(identifier)('println')
      |    ArgumentList
      |      PsiElement(()('(')
      |      IntegerLiteral
      |        PsiElement(integer)('2')
      |      PsiElement())(')')
      |  PsiWhiteSpace('\n')""".stripMargin
  )

  def test_indentation_incomplete_body_followed_by_unindented_expressions_1(): Unit = checkTree(
    """trait MarkerTrait(p: Int)
      |given intOrd3: Ord[Int] with MarkerTrait(42) with
      |println(1)
      |println(2)
      |""".stripMargin,
    """ScalaFile
      |  ScTrait: MarkerTrait
      |    AnnotationsList
      |      <empty list>
      |    Modifiers
      |      <empty list>
      |    PsiElement(trait)('trait')
      |    PsiWhiteSpace(' ')
      |    PsiElement(identifier)('MarkerTrait')
      |    PrimaryConstructor
      |      AnnotationsList
      |        <empty list>
      |      Modifiers
      |        <empty list>
      |      Parameters
      |        ParametersClause
      |          PsiElement(()('(')
      |          ClassParameter: p
      |            AnnotationsList
      |              <empty list>
      |            Modifiers
      |              <empty list>
      |            PsiElement(identifier)('p')
      |            PsiElement(:)(':')
      |            PsiWhiteSpace(' ')
      |            ParameterType
      |              SimpleType: Int
      |                CodeReferenceElement: Int
      |                  PsiElement(identifier)('Int')
      |          PsiElement())(')')
      |    ExtendsBlock
      |      <empty list>
      |  PsiWhiteSpace('\n')
      |  ScGivenDefinition: intOrd3
      |    AnnotationsList
      |      <empty list>
      |    Modifiers
      |      <empty list>
      |    PsiElement(given)('given')
      |    PsiWhiteSpace(' ')
      |    PsiElement(identifier)('intOrd3')
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
      |        PsiWhiteSpace(' ')
      |        PsiElement(with)('with')
      |        PsiWhiteSpace(' ')
      |        ConstructorInvocation
      |          SimpleType: MarkerTrait
      |            CodeReferenceElement: MarkerTrait
      |              PsiElement(identifier)('MarkerTrait')
      |          ArgumentList
      |            PsiElement(()('(')
      |            IntegerLiteral
      |              PsiElement(integer)('42')
      |            PsiElement())(')')
      |      PsiWhiteSpace(' ')
      |      PsiElement(with)('with')
      |      ScTemplateBody
      |        <empty list>
      |  PsiWhiteSpace('\n')
      |  MethodCall
      |    ReferenceExpression: println
      |      PsiElement(identifier)('println')
      |    ArgumentList
      |      PsiElement(()('(')
      |      IntegerLiteral
      |        PsiElement(integer)('1')
      |      PsiElement())(')')
      |  PsiWhiteSpace('\n')
      |  MethodCall
      |    ReferenceExpression: println
      |      PsiElement(identifier)('println')
      |    ArgumentList
      |      PsiElement(()('(')
      |      IntegerLiteral
      |        PsiElement(integer)('2')
      |      PsiElement())(')')
      |  PsiWhiteSpace('\n')""".stripMargin
  )

  def test_incomplete_given_alias_declaration_without_type_annotation(): Unit = checkTree(
    """given value:
      |given value(x: Int):
      |""".stripMargin,
    """ScalaFile
      |  ScGivenAliasDeclaration: value
      |    AnnotationsList
      |      <empty list>
      |    Modifiers
      |      <empty list>
      |    PsiElement(given)('given')
      |    PsiWhiteSpace(' ')
      |    PsiElement(identifier)('value')
      |    Parameters
      |      <empty list>
      |    PsiElement(:)(':')
      |    PsiErrorElement:Wrong type
      |      <empty list>
      |  PsiWhiteSpace('\n')
      |  ScGivenAliasDeclaration: value
      |    AnnotationsList
      |      <empty list>
      |    Modifiers
      |      <empty list>
      |    PsiElement(given)('given')
      |    PsiWhiteSpace(' ')
      |    PsiElement(identifier)('value')
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
      |            SimpleType: Int
      |              CodeReferenceElement: Int
      |                PsiElement(identifier)('Int')
      |        PsiElement())(')')
      |    PsiElement(:)(':')
      |    PsiErrorElement:Wrong type
      |      <empty list>
      |  PsiWhiteSpace('\n')
      |""".stripMargin
  )

  def test_incomplete_given_alias_definition_without_type_annotation(): Unit = checkTree(
    """given value: = ???
      |given value(x: Int): = ???
      |""".stripMargin,
    """ScalaFile
      |  ScGivenAliasDefinition: value
      |    AnnotationsList
      |      <empty list>
      |    Modifiers
      |      <empty list>
      |    PsiElement(given)('given')
      |    PsiWhiteSpace(' ')
      |    PsiElement(identifier)('value')
      |    Parameters
      |      <empty list>
      |    PsiElement(:)(':')
      |    PsiErrorElement:Wrong type
      |      <empty list>
      |    PsiWhiteSpace(' ')
      |    PsiElement(=)('=')
      |    PsiWhiteSpace(' ')
      |    ReferenceExpression: ???
      |      PsiElement(identifier)('???')
      |  PsiWhiteSpace('\n')
      |  ScGivenAliasDefinition: value
      |    AnnotationsList
      |      <empty list>
      |    Modifiers
      |      <empty list>
      |    PsiElement(given)('given')
      |    PsiWhiteSpace(' ')
      |    PsiElement(identifier)('value')
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
      |            SimpleType: Int
      |              CodeReferenceElement: Int
      |                PsiElement(identifier)('Int')
      |        PsiElement())(')')
      |    PsiElement(:)(':')
      |    PsiErrorElement:Wrong type
      |      <empty list>
      |    PsiWhiteSpace(' ')
      |    PsiElement(=)('=')
      |    PsiWhiteSpace(' ')
      |    ReferenceExpression: ???
      |      PsiElement(identifier)('???')
      |  PsiWhiteSpace('\n')
      |""".stripMargin
  )

  def test_incomplete_given_structural_instance_without_constructor_invocation(): Unit = checkTree(
    """given value: with MyTrait with {}
      |""".stripMargin,
    """ScalaFile
      |  ScGivenDefinition: value
      |    AnnotationsList
      |      <empty list>
      |    Modifiers
      |      <empty list>
      |    PsiElement(given)('given')
      |    PsiWhiteSpace(' ')
      |    PsiElement(identifier)('value')
      |    Parameters
      |      <empty list>
      |    PsiElement(:)(':')
      |    PsiWhiteSpace(' ')
      |    ExtendsBlock
      |      TemplateParents
      |        PsiErrorElement:Wrong type
      |          <empty list>
      |        PsiElement(with)('with')
      |        PsiWhiteSpace(' ')
      |        ConstructorInvocation
      |          SimpleType: MyTrait
      |            CodeReferenceElement: MyTrait
      |              PsiElement(identifier)('MyTrait')
      |      PsiWhiteSpace(' ')
      |      PsiElement(with)('with')
      |      PsiWhiteSpace(' ')
      |      ScTemplateBody
      |        PsiElement({)('{')
      |        PsiElement(})('}')
      |  PsiWhiteSpace('\n')
      |""".stripMargin
  )

  def test_full(): Unit = checkTree(
    """
      |given Test[T](using Ord[T]): Ord[Int] with {}
      |""".stripMargin,
    """
      |ScalaFile
      |  PsiWhiteSpace('\n')
      |  ScGivenDefinition: Test
      |    AnnotationsList
      |      <empty list>
      |    Modifiers
      |      <empty list>
      |    PsiElement(given)('given')
      |    PsiWhiteSpace(' ')
      |    PsiElement(identifier)('Test')
      |    TypeParameterClause
      |      PsiElement([)('[')
      |      TypeParameter: T
      |        PsiElement(identifier)('T')
      |      PsiElement(])(']')
      |    Parameters
      |      ParametersClause
      |        PsiElement(()('(')
      |        PsiElement(using)('using')
      |        PsiWhiteSpace(' ')
      |        Parameter: <anonymous>
      |          ParameterType
      |            ParametrizedType: Ord[T]
      |              SimpleType: Ord
      |                CodeReferenceElement: Ord
      |                  PsiElement(identifier)('Ord')
      |              TypeArgumentsList
      |                PsiElement([)('[')
      |                SimpleType: T
      |                  CodeReferenceElement: T
      |                    PsiElement(identifier)('T')
      |                PsiElement(])(']')
      |        PsiElement())(')')
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
      |      PsiWhiteSpace(' ')
      |      ScTemplateBody
      |        PsiElement({)('{')
      |        PsiElement(})('}')
      |  PsiWhiteSpace('\n')
      |""".stripMargin
  )

  def test_without_name(): Unit = checkTree(
    """
      |given [T](using Ord[T]): Ord[Int] with {}
      |""".stripMargin,
    """
      |ScalaFile
      |  PsiWhiteSpace('\n')
      |  ScGivenDefinition: given_Ord_Int
      |    AnnotationsList
      |      <empty list>
      |    Modifiers
      |      <empty list>
      |    PsiElement(given)('given')
      |    PsiWhiteSpace(' ')
      |    TypeParameterClause
      |      PsiElement([)('[')
      |      TypeParameter: T
      |        PsiElement(identifier)('T')
      |      PsiElement(])(']')
      |    Parameters
      |      ParametersClause
      |        PsiElement(()('(')
      |        PsiElement(using)('using')
      |        PsiWhiteSpace(' ')
      |        Parameter: <anonymous>
      |          ParameterType
      |            ParametrizedType: Ord[T]
      |              SimpleType: Ord
      |                CodeReferenceElement: Ord
      |                  PsiElement(identifier)('Ord')
      |              TypeArgumentsList
      |                PsiElement([)('[')
      |                SimpleType: T
      |                  CodeReferenceElement: T
      |                    PsiElement(identifier)('T')
      |                PsiElement(])(']')
      |        PsiElement())(')')
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
      |      PsiWhiteSpace(' ')
      |      ScTemplateBody
      |        PsiElement({)('{')
      |        PsiElement(})('}')
      |  PsiWhiteSpace('\n')
      |""".stripMargin
  )

  def test_without_type_params(): Unit = checkTree(
    """
      |given Test(using Ord[Int]): Ord[Double] with {}
      |""".stripMargin,
    """
      |ScalaFile
      |  PsiWhiteSpace('\n')
      |  ScGivenDefinition: Test
      |    AnnotationsList
      |      <empty list>
      |    Modifiers
      |      <empty list>
      |    PsiElement(given)('given')
      |    PsiWhiteSpace(' ')
      |    PsiElement(identifier)('Test')
      |    Parameters
      |      ParametersClause
      |        PsiElement(()('(')
      |        PsiElement(using)('using')
      |        PsiWhiteSpace(' ')
      |        Parameter: <anonymous>
      |          ParameterType
      |            ParametrizedType: Ord[Int]
      |              SimpleType: Ord
      |                CodeReferenceElement: Ord
      |                  PsiElement(identifier)('Ord')
      |              TypeArgumentsList
      |                PsiElement([)('[')
      |                SimpleType: Int
      |                  CodeReferenceElement: Int
      |                    PsiElement(identifier)('Int')
      |                PsiElement(])(']')
      |        PsiElement())(')')
      |    PsiElement(:)(':')
      |    PsiWhiteSpace(' ')
      |    ExtendsBlock
      |      TemplateParents
      |        ConstructorInvocation
      |          ParametrizedType: Ord[Double]
      |            SimpleType: Ord
      |              CodeReferenceElement: Ord
      |                PsiElement(identifier)('Ord')
      |            TypeArgumentsList
      |              PsiElement([)('[')
      |              SimpleType: Double
      |                CodeReferenceElement: Double
      |                  PsiElement(identifier)('Double')
      |              PsiElement(])(']')
      |      PsiWhiteSpace(' ')
      |      PsiElement(with)('with')
      |      PsiWhiteSpace(' ')
      |      ScTemplateBody
      |        PsiElement({)('{')
      |        PsiElement(})('}')
      |  PsiWhiteSpace('\n')
      |""".stripMargin
  )

  def test_without_params(): Unit = checkTree(
    """
      |given Test[T]: Ord[Int] with {}
      |""".stripMargin,
    """
      |ScalaFile
      |  PsiWhiteSpace('\n')
      |  ScGivenDefinition: Test
      |    AnnotationsList
      |      <empty list>
      |    Modifiers
      |      <empty list>
      |    PsiElement(given)('given')
      |    PsiWhiteSpace(' ')
      |    PsiElement(identifier)('Test')
      |    TypeParameterClause
      |      PsiElement([)('[')
      |      TypeParameter: T
      |        PsiElement(identifier)('T')
      |      PsiElement(])(']')
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
      |      PsiWhiteSpace(' ')
      |      ScTemplateBody
      |        PsiElement({)('{')
      |        PsiElement(})('}')
      |  PsiWhiteSpace('\n')
      |""".stripMargin
  )

  def test_only_type_args(): Unit = checkTree(
    """
      |given [T]: Ord[T] with {}
      |""".stripMargin,
    """
      |ScalaFile
      |  PsiWhiteSpace('\n')
      |  ScGivenDefinition: given_Ord_T
      |    AnnotationsList
      |      <empty list>
      |    Modifiers
      |      <empty list>
      |    PsiElement(given)('given')
      |    PsiWhiteSpace(' ')
      |    TypeParameterClause
      |      PsiElement([)('[')
      |      TypeParameter: T
      |        PsiElement(identifier)('T')
      |      PsiElement(])(']')
      |    Parameters
      |      <empty list>
      |    PsiElement(:)(':')
      |    PsiWhiteSpace(' ')
      |    ExtendsBlock
      |      TemplateParents
      |        ConstructorInvocation
      |          ParametrizedType: Ord[T]
      |            SimpleType: Ord
      |              CodeReferenceElement: Ord
      |                PsiElement(identifier)('Ord')
      |            TypeArgumentsList
      |              PsiElement([)('[')
      |              SimpleType: T
      |                CodeReferenceElement: T
      |                  PsiElement(identifier)('T')
      |              PsiElement(])(']')
      |      PsiWhiteSpace(' ')
      |      PsiElement(with)('with')
      |      PsiWhiteSpace(' ')
      |      ScTemplateBody
      |        PsiElement({)('{')
      |        PsiElement(})('}')
      |  PsiWhiteSpace('\n')
      |""".stripMargin
  )

  def test_only_name(): Unit = checkTree(
    """
      |given Test: Ord[Int] with {}
      |""".stripMargin,
    """
      |ScalaFile
      |  PsiWhiteSpace('\n')
      |  ScGivenDefinition: Test
      |    AnnotationsList
      |      <empty list>
      |    Modifiers
      |      <empty list>
      |    PsiElement(given)('given')
      |    PsiWhiteSpace(' ')
      |    PsiElement(identifier)('Test')
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
      |      PsiWhiteSpace(' ')
      |      ScTemplateBody
      |        PsiElement({)('{')
      |        PsiElement(})('}')
      |  PsiWhiteSpace('\n')
      |""".stripMargin
  )

  def test_constr_app(): Unit = checkTree(
    """
      |given Foo()
      |""".stripMargin,
    """
      |ScalaFile
      |  PsiWhiteSpace('\n')
      |  ScGivenDefinition: given_Foo
      |    AnnotationsList
      |      <empty list>
      |    Modifiers
      |      <empty list>
      |    PsiElement(given)('given')
      |    PsiWhiteSpace(' ')
      |    ExtendsBlock
      |      TemplateParents
      |        ConstructorInvocation
      |          SimpleType: Foo
      |            CodeReferenceElement: Foo
      |              PsiElement(identifier)('Foo')
      |          ArgumentList
      |            PsiElement(()('(')
      |            PsiElement())(')')
      |  PsiWhiteSpace('\n')
      |""".stripMargin
  )

  def test_plain(): Unit = checkTree(
    """
      |given Ord[Int] with {}
      |""".stripMargin,
    """
      |ScalaFile
      |  PsiWhiteSpace('\n')
      |  ScGivenDefinition: given_Ord_Int
      |    AnnotationsList
      |      <empty list>
      |    Modifiers
      |      <empty list>
      |    PsiElement(given)('given')
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
      |      PsiWhiteSpace(' ')
      |      ScTemplateBody
      |        PsiElement({)('{')
      |        PsiElement(})('}')
      |  PsiWhiteSpace('\n')
      |""".stripMargin
  )

  def test_without_sig(): Unit = checkTree(
    """
      |given Test with {}
      |""".stripMargin,
    """
      |ScalaFile
      |  PsiWhiteSpace('\n')
      |  ScGivenDefinition: given_Test
      |    AnnotationsList
      |      <empty list>
      |    Modifiers
      |      <empty list>
      |    PsiElement(given)('given')
      |    PsiWhiteSpace(' ')
      |    ExtendsBlock
      |      TemplateParents
      |        ConstructorInvocation
      |          SimpleType: Test
      |            CodeReferenceElement: Test
      |              PsiElement(identifier)('Test')
      |      PsiWhiteSpace(' ')
      |      PsiElement(with)('with')
      |      PsiWhiteSpace(' ')
      |      ScTemplateBody
      |        PsiElement({)('{')
      |        PsiElement(})('}')
      |  PsiWhiteSpace('\n')
      |""".stripMargin
  )


  /********************************** with Template body *********************************************/


  def test_full_alias(): Unit = checkTree(
    """
      |given Test[T](using Ord[T]): Ord[Int] = ???
      |""".stripMargin,
    """
      |ScalaFile
      |  PsiWhiteSpace('\n')
      |  ScGivenAliasDefinition: Test
      |    AnnotationsList
      |      <empty list>
      |    Modifiers
      |      <empty list>
      |    PsiElement(given)('given')
      |    PsiWhiteSpace(' ')
      |    PsiElement(identifier)('Test')
      |    TypeParameterClause
      |      PsiElement([)('[')
      |      TypeParameter: T
      |        PsiElement(identifier)('T')
      |      PsiElement(])(']')
      |    Parameters
      |      ParametersClause
      |        PsiElement(()('(')
      |        PsiElement(using)('using')
      |        PsiWhiteSpace(' ')
      |        Parameter: <anonymous>
      |          ParameterType
      |            ParametrizedType: Ord[T]
      |              SimpleType: Ord
      |                CodeReferenceElement: Ord
      |                  PsiElement(identifier)('Ord')
      |              TypeArgumentsList
      |                PsiElement([)('[')
      |                SimpleType: T
      |                  CodeReferenceElement: T
      |                    PsiElement(identifier)('T')
      |                PsiElement(])(']')
      |        PsiElement())(')')
      |    PsiElement(:)(':')
      |    PsiWhiteSpace(' ')
      |    ParametrizedType: Ord[Int]
      |      SimpleType: Ord
      |        CodeReferenceElement: Ord
      |          PsiElement(identifier)('Ord')
      |      TypeArgumentsList
      |        PsiElement([)('[')
      |        SimpleType: Int
      |          CodeReferenceElement: Int
      |            PsiElement(identifier)('Int')
      |        PsiElement(])(']')
      |    PsiWhiteSpace(' ')
      |    PsiElement(=)('=')
      |    PsiWhiteSpace(' ')
      |    ReferenceExpression: ???
      |      PsiElement(identifier)('???')
      |  PsiWhiteSpace('\n')
      |""".stripMargin
  )

  def test_alias_without_name(): Unit = checkTree(
    """
      |given [T](using Ord[T]): Ord[Int] = ???
      |""".stripMargin,
    """
      |ScalaFile
      |  PsiWhiteSpace('\n')
      |  ScGivenAliasDefinition: given_Ord_Int
      |    AnnotationsList
      |      <empty list>
      |    Modifiers
      |      <empty list>
      |    PsiElement(given)('given')
      |    PsiWhiteSpace(' ')
      |    TypeParameterClause
      |      PsiElement([)('[')
      |      TypeParameter: T
      |        PsiElement(identifier)('T')
      |      PsiElement(])(']')
      |    Parameters
      |      ParametersClause
      |        PsiElement(()('(')
      |        PsiElement(using)('using')
      |        PsiWhiteSpace(' ')
      |        Parameter: <anonymous>
      |          ParameterType
      |            ParametrizedType: Ord[T]
      |              SimpleType: Ord
      |                CodeReferenceElement: Ord
      |                  PsiElement(identifier)('Ord')
      |              TypeArgumentsList
      |                PsiElement([)('[')
      |                SimpleType: T
      |                  CodeReferenceElement: T
      |                    PsiElement(identifier)('T')
      |                PsiElement(])(']')
      |        PsiElement())(')')
      |    PsiElement(:)(':')
      |    PsiWhiteSpace(' ')
      |    ParametrizedType: Ord[Int]
      |      SimpleType: Ord
      |        CodeReferenceElement: Ord
      |          PsiElement(identifier)('Ord')
      |      TypeArgumentsList
      |        PsiElement([)('[')
      |        SimpleType: Int
      |          CodeReferenceElement: Int
      |            PsiElement(identifier)('Int')
      |        PsiElement(])(']')
      |    PsiWhiteSpace(' ')
      |    PsiElement(=)('=')
      |    PsiWhiteSpace(' ')
      |    ReferenceExpression: ???
      |      PsiElement(identifier)('???')
      |  PsiWhiteSpace('\n')
      |""".stripMargin
  )

  def test_alias_without_type_params(): Unit = checkTree(
    """
      |given Test(using Ord[Int]): Ord[Double] = ???
      |""".stripMargin,
    """
      |ScalaFile
      |  PsiWhiteSpace('\n')
      |  ScGivenAliasDefinition: Test
      |    AnnotationsList
      |      <empty list>
      |    Modifiers
      |      <empty list>
      |    PsiElement(given)('given')
      |    PsiWhiteSpace(' ')
      |    PsiElement(identifier)('Test')
      |    Parameters
      |      ParametersClause
      |        PsiElement(()('(')
      |        PsiElement(using)('using')
      |        PsiWhiteSpace(' ')
      |        Parameter: <anonymous>
      |          ParameterType
      |            ParametrizedType: Ord[Int]
      |              SimpleType: Ord
      |                CodeReferenceElement: Ord
      |                  PsiElement(identifier)('Ord')
      |              TypeArgumentsList
      |                PsiElement([)('[')
      |                SimpleType: Int
      |                  CodeReferenceElement: Int
      |                    PsiElement(identifier)('Int')
      |                PsiElement(])(']')
      |        PsiElement())(')')
      |    PsiElement(:)(':')
      |    PsiWhiteSpace(' ')
      |    ParametrizedType: Ord[Double]
      |      SimpleType: Ord
      |        CodeReferenceElement: Ord
      |          PsiElement(identifier)('Ord')
      |      TypeArgumentsList
      |        PsiElement([)('[')
      |        SimpleType: Double
      |          CodeReferenceElement: Double
      |            PsiElement(identifier)('Double')
      |        PsiElement(])(']')
      |    PsiWhiteSpace(' ')
      |    PsiElement(=)('=')
      |    PsiWhiteSpace(' ')
      |    ReferenceExpression: ???
      |      PsiElement(identifier)('???')
      |  PsiWhiteSpace('\n')
      |""".stripMargin
  )

  def test_alias_without_params(): Unit = checkTree(
    """
      |given Test[T]: Ord[Int] = ???
      |""".stripMargin,
    """
      |ScalaFile
      |  PsiWhiteSpace('\n')
      |  ScGivenAliasDefinition: Test
      |    AnnotationsList
      |      <empty list>
      |    Modifiers
      |      <empty list>
      |    PsiElement(given)('given')
      |    PsiWhiteSpace(' ')
      |    PsiElement(identifier)('Test')
      |    TypeParameterClause
      |      PsiElement([)('[')
      |      TypeParameter: T
      |        PsiElement(identifier)('T')
      |      PsiElement(])(']')
      |    Parameters
      |      <empty list>
      |    PsiElement(:)(':')
      |    PsiWhiteSpace(' ')
      |    ParametrizedType: Ord[Int]
      |      SimpleType: Ord
      |        CodeReferenceElement: Ord
      |          PsiElement(identifier)('Ord')
      |      TypeArgumentsList
      |        PsiElement([)('[')
      |        SimpleType: Int
      |          CodeReferenceElement: Int
      |            PsiElement(identifier)('Int')
      |        PsiElement(])(']')
      |    PsiWhiteSpace(' ')
      |    PsiElement(=)('=')
      |    PsiWhiteSpace(' ')
      |    ReferenceExpression: ???
      |      PsiElement(identifier)('???')
      |  PsiWhiteSpace('\n')
      |""".stripMargin
  )

  def test_alias_only_type_args(): Unit = checkTree(
    """
      |given [T]: Ord[T] = ???
      |""".stripMargin,
    """
      |ScalaFile
      |  PsiWhiteSpace('\n')
      |  ScGivenAliasDefinition: given_Ord_T
      |    AnnotationsList
      |      <empty list>
      |    Modifiers
      |      <empty list>
      |    PsiElement(given)('given')
      |    PsiWhiteSpace(' ')
      |    TypeParameterClause
      |      PsiElement([)('[')
      |      TypeParameter: T
      |        PsiElement(identifier)('T')
      |      PsiElement(])(']')
      |    Parameters
      |      <empty list>
      |    PsiElement(:)(':')
      |    PsiWhiteSpace(' ')
      |    ParametrizedType: Ord[T]
      |      SimpleType: Ord
      |        CodeReferenceElement: Ord
      |          PsiElement(identifier)('Ord')
      |      TypeArgumentsList
      |        PsiElement([)('[')
      |        SimpleType: T
      |          CodeReferenceElement: T
      |            PsiElement(identifier)('T')
      |        PsiElement(])(']')
      |    PsiWhiteSpace(' ')
      |    PsiElement(=)('=')
      |    PsiWhiteSpace(' ')
      |    ReferenceExpression: ???
      |      PsiElement(identifier)('???')
      |  PsiWhiteSpace('\n')
      |""".stripMargin
  )

  def test_alias_only_name(): Unit = checkTree(
    """
      |given Test: Ord[Int] = ???
      |""".stripMargin,
    """
      |ScalaFile
      |  PsiWhiteSpace('\n')
      |  ScGivenAliasDefinition: Test
      |    AnnotationsList
      |      <empty list>
      |    Modifiers
      |      <empty list>
      |    PsiElement(given)('given')
      |    PsiWhiteSpace(' ')
      |    PsiElement(identifier)('Test')
      |    Parameters
      |      <empty list>
      |    PsiElement(:)(':')
      |    PsiWhiteSpace(' ')
      |    ParametrizedType: Ord[Int]
      |      SimpleType: Ord
      |        CodeReferenceElement: Ord
      |          PsiElement(identifier)('Ord')
      |      TypeArgumentsList
      |        PsiElement([)('[')
      |        SimpleType: Int
      |          CodeReferenceElement: Int
      |            PsiElement(identifier)('Int')
      |        PsiElement(])(']')
      |    PsiWhiteSpace(' ')
      |    PsiElement(=)('=')
      |    PsiWhiteSpace(' ')
      |    ReferenceExpression: ???
      |      PsiElement(identifier)('???')
      |  PsiWhiteSpace('\n')
      |""".stripMargin
  )

  def test_alias_plain(): Unit = checkTree(
    """
      |given Ord[Int] = ???
      |""".stripMargin,
    """
      |ScalaFile
      |  PsiWhiteSpace('\n')
      |  ScGivenAliasDefinition: given_Ord_Int
      |    AnnotationsList
      |      <empty list>
      |    Modifiers
      |      <empty list>
      |    PsiElement(given)('given')
      |    PsiWhiteSpace(' ')
      |    ParametrizedType: Ord[Int]
      |      SimpleType: Ord
      |        CodeReferenceElement: Ord
      |          PsiElement(identifier)('Ord')
      |      TypeArgumentsList
      |        PsiElement([)('[')
      |        SimpleType: Int
      |          CodeReferenceElement: Int
      |            PsiElement(identifier)('Int')
      |        PsiElement(])(']')
      |    Parameters
      |      <empty list>
      |    PsiWhiteSpace(' ')
      |    PsiElement(=)('=')
      |    PsiWhiteSpace(' ')
      |    ReferenceExpression: ???
      |      PsiElement(identifier)('???')
      |  PsiWhiteSpace('\n')
      |""".stripMargin
  )

  def test_alias_without_sig(): Unit = checkTree(
    """
      |given Test = ???
      |""".stripMargin,
    """
      |ScalaFile
      |  PsiWhiteSpace('\n')
      |  ScGivenAliasDefinition: given_Test
      |    AnnotationsList
      |      <empty list>
      |    Modifiers
      |      <empty list>
      |    PsiElement(given)('given')
      |    PsiWhiteSpace(' ')
      |    SimpleType: Test
      |      CodeReferenceElement: Test
      |        PsiElement(identifier)('Test')
      |    Parameters
      |      <empty list>
      |    PsiWhiteSpace(' ')
      |    PsiElement(=)('=')
      |    PsiWhiteSpace(' ')
      |    ReferenceExpression: ???
      |      PsiElement(identifier)('???')
      |  PsiWhiteSpace('\n')
      |""".stripMargin
  )

  /********************************** alias declarations *********************************************/

  def test_full_alias_declaration(): Unit = checkTree(
    """
      |given Test[T](using Ord[T]): Ord[Int]
      |""".stripMargin,
    """
      |ScalaFile
      |  PsiWhiteSpace('\n')
      |  ScGivenAliasDeclaration: Test
      |    AnnotationsList
      |      <empty list>
      |    Modifiers
      |      <empty list>
      |    PsiElement(given)('given')
      |    PsiWhiteSpace(' ')
      |    PsiElement(identifier)('Test')
      |    TypeParameterClause
      |      PsiElement([)('[')
      |      TypeParameter: T
      |        PsiElement(identifier)('T')
      |      PsiElement(])(']')
      |    Parameters
      |      ParametersClause
      |        PsiElement(()('(')
      |        PsiElement(using)('using')
      |        PsiWhiteSpace(' ')
      |        Parameter: <anonymous>
      |          ParameterType
      |            ParametrizedType: Ord[T]
      |              SimpleType: Ord
      |                CodeReferenceElement: Ord
      |                  PsiElement(identifier)('Ord')
      |              TypeArgumentsList
      |                PsiElement([)('[')
      |                SimpleType: T
      |                  CodeReferenceElement: T
      |                    PsiElement(identifier)('T')
      |                PsiElement(])(']')
      |        PsiElement())(')')
      |    PsiElement(:)(':')
      |    PsiWhiteSpace(' ')
      |    ParametrizedType: Ord[Int]
      |      SimpleType: Ord
      |        CodeReferenceElement: Ord
      |          PsiElement(identifier)('Ord')
      |      TypeArgumentsList
      |        PsiElement([)('[')
      |        SimpleType: Int
      |          CodeReferenceElement: Int
      |            PsiElement(identifier)('Int')
      |        PsiElement(])(']')
      |  PsiWhiteSpace('\n')
      |""".stripMargin
  )

  def test_anonymous_alias_declaration(): Unit = checkTree(
    """
      |given [T](using Ord[T]): Ord[Int]
      |""".stripMargin,
    """
      |ScalaFile
      |  PsiWhiteSpace('\n')
      |  ScGivenAliasDeclaration: given_Ord_Int
      |    AnnotationsList
      |      <empty list>
      |    Modifiers
      |      <empty list>
      |    PsiElement(given)('given')
      |    PsiWhiteSpace(' ')
      |    TypeParameterClause
      |      PsiElement([)('[')
      |      TypeParameter: T
      |        PsiElement(identifier)('T')
      |      PsiElement(])(']')
      |    Parameters
      |      ParametersClause
      |        PsiElement(()('(')
      |        PsiElement(using)('using')
      |        PsiWhiteSpace(' ')
      |        Parameter: <anonymous>
      |          ParameterType
      |            ParametrizedType: Ord[T]
      |              SimpleType: Ord
      |                CodeReferenceElement: Ord
      |                  PsiElement(identifier)('Ord')
      |              TypeArgumentsList
      |                PsiElement([)('[')
      |                SimpleType: T
      |                  CodeReferenceElement: T
      |                    PsiElement(identifier)('T')
      |                PsiElement(])(']')
      |        PsiElement())(')')
      |    PsiElement(:)(':')
      |    PsiWhiteSpace(' ')
      |    ParametrizedType: Ord[Int]
      |      SimpleType: Ord
      |        CodeReferenceElement: Ord
      |          PsiElement(identifier)('Ord')
      |      TypeArgumentsList
      |        PsiElement([)('[')
      |        SimpleType: Int
      |          CodeReferenceElement: Int
      |            PsiElement(identifier)('Int')
      |        PsiElement(])(']')
      |  PsiWhiteSpace('\n')
      |""".stripMargin
  )

  def test_alias_declaration_without_type_params(): Unit = checkTree(
    """
      |given Test(using Ord[Int]): Ord[Double]
      |""".stripMargin,
    """
      |ScalaFile
      |  PsiWhiteSpace('\n')
      |  ScGivenAliasDeclaration: Test
      |    AnnotationsList
      |      <empty list>
      |    Modifiers
      |      <empty list>
      |    PsiElement(given)('given')
      |    PsiWhiteSpace(' ')
      |    PsiElement(identifier)('Test')
      |    Parameters
      |      ParametersClause
      |        PsiElement(()('(')
      |        PsiElement(using)('using')
      |        PsiWhiteSpace(' ')
      |        Parameter: <anonymous>
      |          ParameterType
      |            ParametrizedType: Ord[Int]
      |              SimpleType: Ord
      |                CodeReferenceElement: Ord
      |                  PsiElement(identifier)('Ord')
      |              TypeArgumentsList
      |                PsiElement([)('[')
      |                SimpleType: Int
      |                  CodeReferenceElement: Int
      |                    PsiElement(identifier)('Int')
      |                PsiElement(])(']')
      |        PsiElement())(')')
      |    PsiElement(:)(':')
      |    PsiWhiteSpace(' ')
      |    ParametrizedType: Ord[Double]
      |      SimpleType: Ord
      |        CodeReferenceElement: Ord
      |          PsiElement(identifier)('Ord')
      |      TypeArgumentsList
      |        PsiElement([)('[')
      |        SimpleType: Double
      |          CodeReferenceElement: Double
      |            PsiElement(identifier)('Double')
      |        PsiElement(])(']')
      |  PsiWhiteSpace('\n')
      |""".stripMargin
  )

  def test_alias_declaration_without_params(): Unit = checkTree(
    """
      |given Test[T]: Ord[Int]
      |""".stripMargin,
    """
      |ScalaFile
      |  PsiWhiteSpace('\n')
      |  ScGivenAliasDeclaration: Test
      |    AnnotationsList
      |      <empty list>
      |    Modifiers
      |      <empty list>
      |    PsiElement(given)('given')
      |    PsiWhiteSpace(' ')
      |    PsiElement(identifier)('Test')
      |    TypeParameterClause
      |      PsiElement([)('[')
      |      TypeParameter: T
      |        PsiElement(identifier)('T')
      |      PsiElement(])(']')
      |    Parameters
      |      <empty list>
      |    PsiElement(:)(':')
      |    PsiWhiteSpace(' ')
      |    ParametrizedType: Ord[Int]
      |      SimpleType: Ord
      |        CodeReferenceElement: Ord
      |          PsiElement(identifier)('Ord')
      |      TypeArgumentsList
      |        PsiElement([)('[')
      |        SimpleType: Int
      |          CodeReferenceElement: Int
      |            PsiElement(identifier)('Int')
      |        PsiElement(])(']')
      |  PsiWhiteSpace('\n')
      |""".stripMargin
  )

  def test_alias_declaration_with_only_type_args(): Unit = checkTree(
    """
      |given [T]: Ord[T]
      |""".stripMargin,
    """
      |ScalaFile
      |  PsiWhiteSpace('\n')
      |  ScGivenAliasDeclaration: given_Ord_T
      |    AnnotationsList
      |      <empty list>
      |    Modifiers
      |      <empty list>
      |    PsiElement(given)('given')
      |    PsiWhiteSpace(' ')
      |    TypeParameterClause
      |      PsiElement([)('[')
      |      TypeParameter: T
      |        PsiElement(identifier)('T')
      |      PsiElement(])(']')
      |    Parameters
      |      <empty list>
      |    PsiElement(:)(':')
      |    PsiWhiteSpace(' ')
      |    ParametrizedType: Ord[T]
      |      SimpleType: Ord
      |        CodeReferenceElement: Ord
      |          PsiElement(identifier)('Ord')
      |      TypeArgumentsList
      |        PsiElement([)('[')
      |        SimpleType: T
      |          CodeReferenceElement: T
      |            PsiElement(identifier)('T')
      |        PsiElement(])(']')
      |  PsiWhiteSpace('\n')
      |""".stripMargin
  )

  def test_alias_declaration_with_only_name(): Unit = checkTree(
    """
      |given Test: Ord[Int]
      |""".stripMargin,
    """
      |ScalaFile
      |  PsiWhiteSpace('\n')
      |  ScGivenAliasDeclaration: Test
      |    AnnotationsList
      |      <empty list>
      |    Modifiers
      |      <empty list>
      |    PsiElement(given)('given')
      |    PsiWhiteSpace(' ')
      |    PsiElement(identifier)('Test')
      |    Parameters
      |      <empty list>
      |    PsiElement(:)(':')
      |    PsiWhiteSpace(' ')
      |    ParametrizedType: Ord[Int]
      |      SimpleType: Ord
      |        CodeReferenceElement: Ord
      |          PsiElement(identifier)('Ord')
      |      TypeArgumentsList
      |        PsiElement([)('[')
      |        SimpleType: Int
      |          CodeReferenceElement: Int
      |            PsiElement(identifier)('Int')
      |        PsiElement(])(']')
      |  PsiWhiteSpace('\n')
      |""".stripMargin
  )

  def test_anonymous_alias_declaration_plain(): Unit = checkTree(
    """
      |given Ord[Int]
      |""".stripMargin,
    """
      |ScalaFile
      |  PsiWhiteSpace('\n')
      |  ScGivenAliasDeclaration: given_Ord_Int
      |    AnnotationsList
      |      <empty list>
      |    Modifiers
      |      <empty list>
      |    PsiElement(given)('given')
      |    PsiWhiteSpace(' ')
      |    ParametrizedType: Ord[Int]
      |      SimpleType: Ord
      |        CodeReferenceElement: Ord
      |          PsiElement(identifier)('Ord')
      |      TypeArgumentsList
      |        PsiElement([)('[')
      |        SimpleType: Int
      |          CodeReferenceElement: Int
      |            PsiElement(identifier)('Int')
      |        PsiElement(])(']')
      |    Parameters
      |      <empty list>
      |  PsiWhiteSpace('\n')
      |""".stripMargin
  )

  def test_alias_declaration_without_sig(): Unit = checkTree(
    """
      |given Test
      |""".stripMargin,
    """
      |ScalaFile
      |  PsiWhiteSpace('\n')
      |  ScGivenAliasDeclaration: given_Test
      |    AnnotationsList
      |      <empty list>
      |    Modifiers
      |      <empty list>
      |    PsiElement(given)('given')
      |    PsiWhiteSpace(' ')
      |    SimpleType: Test
      |      CodeReferenceElement: Test
      |        PsiElement(identifier)('Test')
      |    Parameters
      |      <empty list>
      |  PsiWhiteSpace('\n')
      |""".stripMargin
  )

  def test_incomplete_given_aliases(): Unit = checkTree(
    """
      |object A {
      |  given ()
      |  given (using )
      |  given (using String)
      |  given (using String): String
      |  given (using String): String =
      |  given (using String): String = ???
      |}
      |""".stripMargin,
    """
      |ScalaFile
      |  PsiWhiteSpace('\n')
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
      |        ScGivenAliasDeclaration: given_
      |          AnnotationsList
      |            <empty list>
      |          Modifiers
      |            <empty list>
      |          PsiElement(given)('given')
      |          PsiWhiteSpace(' ')
      |          TypeInParenthesis: ()
      |            PsiElement(()('(')
      |            PsiElement())(')')
      |          Parameters
      |            <empty list>
      |        PsiWhiteSpace('\n  ')
      |        ScGivenAliasDeclaration: given_
      |          AnnotationsList
      |            <empty list>
      |          Modifiers
      |            <empty list>
      |          PsiElement(given)('given')
      |          PsiWhiteSpace(' ')
      |          Parameters
      |            ParametersClause
      |              PsiElement(()('(')
      |              PsiElement(using)('using')
      |              PsiErrorElement:Identifier expected
      |                <empty list>
      |              PsiWhiteSpace(' ')
      |              PsiElement())(')')
      |          PsiErrorElement:':' expected
      |            <empty list>
      |        PsiWhiteSpace('\n  ')
      |        ScGivenAliasDeclaration: given_
      |          AnnotationsList
      |            <empty list>
      |          Modifiers
      |            <empty list>
      |          PsiElement(given)('given')
      |          PsiWhiteSpace(' ')
      |          Parameters
      |            ParametersClause
      |              PsiElement(()('(')
      |              PsiElement(using)('using')
      |              PsiWhiteSpace(' ')
      |              Parameter: <anonymous>
      |                ParameterType
      |                  SimpleType: String
      |                    CodeReferenceElement: String
      |                      PsiElement(identifier)('String')
      |              PsiElement())(')')
      |          PsiErrorElement:':' expected
      |            <empty list>
      |        PsiWhiteSpace('\n  ')
      |        ScGivenAliasDeclaration: given_String
      |          AnnotationsList
      |            <empty list>
      |          Modifiers
      |            <empty list>
      |          PsiElement(given)('given')
      |          PsiWhiteSpace(' ')
      |          Parameters
      |            ParametersClause
      |              PsiElement(()('(')
      |              PsiElement(using)('using')
      |              PsiWhiteSpace(' ')
      |              Parameter: <anonymous>
      |                ParameterType
      |                  SimpleType: String
      |                    CodeReferenceElement: String
      |                      PsiElement(identifier)('String')
      |              PsiElement())(')')
      |          PsiElement(:)(':')
      |          PsiWhiteSpace(' ')
      |          SimpleType: String
      |            CodeReferenceElement: String
      |              PsiElement(identifier)('String')
      |        PsiWhiteSpace('\n  ')
      |        ScGivenAliasDefinition: given_String
      |          AnnotationsList
      |            <empty list>
      |          Modifiers
      |            <empty list>
      |          PsiElement(given)('given')
      |          PsiWhiteSpace(' ')
      |          Parameters
      |            ParametersClause
      |              PsiElement(()('(')
      |              PsiElement(using)('using')
      |              PsiWhiteSpace(' ')
      |              Parameter: <anonymous>
      |                ParameterType
      |                  SimpleType: String
      |                    CodeReferenceElement: String
      |                      PsiElement(identifier)('String')
      |              PsiElement())(')')
      |          PsiElement(:)(':')
      |          PsiWhiteSpace(' ')
      |          SimpleType: String
      |            CodeReferenceElement: String
      |              PsiElement(identifier)('String')
      |          PsiWhiteSpace(' ')
      |          PsiElement(=)('=')
      |          PsiErrorElement:Expression expected
      |            <empty list>
      |        PsiWhiteSpace('\n  ')
      |        ScGivenAliasDefinition: given_String
      |          AnnotationsList
      |            <empty list>
      |          Modifiers
      |            <empty list>
      |          PsiElement(given)('given')
      |          PsiWhiteSpace(' ')
      |          Parameters
      |            ParametersClause
      |              PsiElement(()('(')
      |              PsiElement(using)('using')
      |              PsiWhiteSpace(' ')
      |              Parameter: <anonymous>
      |                ParameterType
      |                  SimpleType: String
      |                    CodeReferenceElement: String
      |                      PsiElement(identifier)('String')
      |              PsiElement())(')')
      |          PsiElement(:)(':')
      |          PsiWhiteSpace(' ')
      |          SimpleType: String
      |            CodeReferenceElement: String
      |              PsiElement(identifier)('String')
      |          PsiWhiteSpace(' ')
      |          PsiElement(=)('=')
      |          PsiWhiteSpace(' ')
      |          ReferenceExpression: ???
      |            PsiElement(identifier)('???')
      |        PsiWhiteSpace('\n')
      |        PsiElement(})('}')
      |  PsiWhiteSpace('\n')
      |""".stripMargin
  )

  def test_incomplete_given_sig_with_type_params(): Unit = checkTree(
    """
      |object B {
      |  given []
      |  given [T]
      |  given [T]()
      |  given [T](using )
      |}
      |""".stripMargin,
    """
      |ScalaFile
      |  PsiWhiteSpace('\n')
      |  ScObject: B
      |    AnnotationsList
      |      <empty list>
      |    Modifiers
      |      <empty list>
      |    PsiElement(object)('object')
      |    PsiWhiteSpace(' ')
      |    PsiElement(identifier)('B')
      |    PsiWhiteSpace(' ')
      |    ExtendsBlock
      |      ScTemplateBody
      |        PsiElement({)('{')
      |        PsiWhiteSpace('\n  ')
      |        ScGivenAliasDeclaration: given_
      |          AnnotationsList
      |            <empty list>
      |          Modifiers
      |            <empty list>
      |          PsiElement(given)('given')
      |          PsiWhiteSpace(' ')
      |          TypeParameterClause
      |            PsiElement([)('[')
      |            PsiErrorElement:Wrong parameter
      |              <empty list>
      |            PsiElement(])(']')
      |          Parameters
      |            <empty list>
      |          PsiErrorElement:':' expected
      |            <empty list>
      |        PsiWhiteSpace('\n  ')
      |        ScGivenAliasDeclaration: given_
      |          AnnotationsList
      |            <empty list>
      |          Modifiers
      |            <empty list>
      |          PsiElement(given)('given')
      |          PsiWhiteSpace(' ')
      |          TypeParameterClause
      |            PsiElement([)('[')
      |            TypeParameter: T
      |              PsiElement(identifier)('T')
      |            PsiElement(])(']')
      |          Parameters
      |            <empty list>
      |          PsiErrorElement:':' expected
      |            <empty list>
      |        PsiWhiteSpace('\n  ')
      |        ScGivenAliasDeclaration: given_
      |          AnnotationsList
      |            <empty list>
      |          Modifiers
      |            <empty list>
      |          PsiElement(given)('given')
      |          PsiWhiteSpace(' ')
      |          TypeParameterClause
      |            PsiElement([)('[')
      |            TypeParameter: T
      |              PsiElement(identifier)('T')
      |            PsiElement(])(']')
      |          Parameters
      |            ParametersClause
      |              PsiElement(()('(')
      |              PsiElement())(')')
      |          PsiErrorElement:':' expected
      |            <empty list>
      |        PsiWhiteSpace('\n  ')
      |        ScGivenAliasDeclaration: given_
      |          AnnotationsList
      |            <empty list>
      |          Modifiers
      |            <empty list>
      |          PsiElement(given)('given')
      |          PsiWhiteSpace(' ')
      |          TypeParameterClause
      |            PsiElement([)('[')
      |            TypeParameter: T
      |              PsiElement(identifier)('T')
      |            PsiElement(])(']')
      |          Parameters
      |            ParametersClause
      |              PsiElement(()('(')
      |              PsiElement(using)('using')
      |              PsiErrorElement:Identifier expected
      |                <empty list>
      |              PsiWhiteSpace(' ')
      |              PsiElement())(')')
      |          PsiErrorElement:':' expected
      |            <empty list>
      |        PsiWhiteSpace('\n')
      |        PsiElement(})('}')
      |  PsiWhiteSpace('\n')
      |""".stripMargin
  )


  def test_given_definition_that_looks_like_given_signature(): Unit = checkTree(
    """
      |object B {
      |  given test[T](using 3)   // <- given definition
      |  given test(using 3)      // <- given definition
      |  given test(using 3): Int // <- given alias
      |}
      |""".stripMargin,
    """
      |ScalaFile
      |  PsiWhiteSpace('\n')
      |  ScObject: B
      |    AnnotationsList
      |      <empty list>
      |    Modifiers
      |      <empty list>
      |    PsiElement(object)('object')
      |    PsiWhiteSpace(' ')
      |    PsiElement(identifier)('B')
      |    PsiWhiteSpace(' ')
      |    ExtendsBlock
      |      ScTemplateBody
      |        PsiElement({)('{')
      |        PsiWhiteSpace('\n  ')
      |        ScGivenDefinition: given_test_T
      |          AnnotationsList
      |            <empty list>
      |          Modifiers
      |            <empty list>
      |          PsiElement(given)('given')
      |          PsiWhiteSpace(' ')
      |          ExtendsBlock
      |            TemplateParents
      |              ConstructorInvocation
      |                ParametrizedType: test[T]
      |                  SimpleType: test
      |                    CodeReferenceElement: test
      |                      PsiElement(identifier)('test')
      |                  TypeArgumentsList
      |                    PsiElement([)('[')
      |                    SimpleType: T
      |                      CodeReferenceElement: T
      |                        PsiElement(identifier)('T')
      |                    PsiElement(])(']')
      |                ArgumentList
      |                  PsiElement(()('(')
      |                  PsiElement(using)('using')
      |                  PsiWhiteSpace(' ')
      |                  IntegerLiteral
      |                    PsiElement(integer)('3')
      |                  PsiElement())(')')
      |        PsiWhiteSpace('   ')
      |        PsiComment(comment)('// <- given definition')
      |        PsiWhiteSpace('\n  ')
      |        ScGivenDefinition: given_test
      |          AnnotationsList
      |            <empty list>
      |          Modifiers
      |            <empty list>
      |          PsiElement(given)('given')
      |          PsiWhiteSpace(' ')
      |          ExtendsBlock
      |            TemplateParents
      |              ConstructorInvocation
      |                SimpleType: test
      |                  CodeReferenceElement: test
      |                    PsiElement(identifier)('test')
      |                ArgumentList
      |                  PsiElement(()('(')
      |                  PsiElement(using)('using')
      |                  PsiWhiteSpace(' ')
      |                  IntegerLiteral
      |                    PsiElement(integer)('3')
      |                  PsiElement())(')')
      |        PsiWhiteSpace('      ')
      |        PsiComment(comment)('// <- given definition')
      |        PsiWhiteSpace('\n  ')
      |        ScGivenAliasDeclaration: test
      |          AnnotationsList
      |            <empty list>
      |          Modifiers
      |            <empty list>
      |          PsiElement(given)('given')
      |          PsiWhiteSpace(' ')
      |          PsiElement(identifier)('test')
      |          Parameters
      |            ParametersClause
      |              PsiElement(()('(')
      |              PsiElement(using)('using')
      |              PsiWhiteSpace(' ')
      |              Parameter: <anonymous>
      |                ParameterType
      |                  LiteralType: 3
      |                    IntegerLiteral
      |                      PsiElement(integer)('3')
      |              PsiElement())(')')
      |          PsiElement(:)(':')
      |          PsiWhiteSpace(' ')
      |          SimpleType: Int
      |            CodeReferenceElement: Int
      |              PsiElement(identifier)('Int')
      |        PsiWhiteSpace(' ')
      |        PsiComment(comment)('// <- given alias')
      |        PsiWhiteSpace('\n')
      |        PsiElement(})('}')
      |  PsiWhiteSpace('\n')
      |""".stripMargin
  )
}
