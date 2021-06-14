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
      |        Parameter: _
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
      |        Parameter: _
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

  def test_without_tpye_params(): Unit = checkTree(
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
      |        Parameter: _
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
      |  ScGivenAlias: Test
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
      |        Parameter: _
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
      |  ScGivenAlias: given_Ord_Int
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
      |        Parameter: _
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
      |  ScGivenAlias: Test
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
      |        Parameter: _
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
      |  ScGivenAlias: Test
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
      |  ScGivenAlias: given_Ord_T
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
      |  ScGivenAlias: Test
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
      |  ScGivenAlias: given_Ord_Int
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
      |  ScGivenAlias: given_Test
      |    AnnotationsList
      |      <empty list>
      |    Modifiers
      |      <empty list>
      |    PsiElement(given)('given')
      |    PsiWhiteSpace(' ')
      |    SimpleType: Test
      |      CodeReferenceElement: Test
      |        PsiElement(identifier)('Test')
      |    PsiWhiteSpace(' ')
      |    PsiElement(=)('=')
      |    PsiWhiteSpace(' ')
      |    ReferenceExpression: ???
      |      PsiElement(identifier)('???')
      |  PsiWhiteSpace('\n')
      |""".stripMargin
  )
}
