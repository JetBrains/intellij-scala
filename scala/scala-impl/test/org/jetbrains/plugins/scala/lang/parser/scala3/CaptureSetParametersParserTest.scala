package org.jetbrains.plugins.scala.lang.parser.scala3

import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.ScalaVersion
import org.jetbrains.plugins.scala.base.CaptureCheckingTestUtils
import org.jetbrains.plugins.scala.lang.parser.ScalaElementType._
import org.jetbrains.plugins.scala.lang.parser.scala3.CaptureCheckingParserTestUtils._
import org.jetbrains.plugins.scala.project.ScalaFeatures
import org.jetbrains.plugins.scala.util.TestUtils
import org.junit.Assert.assertEquals

import java.nio.file.{Files, Path}

/**
 * Tests capture-set parameter and bound syntax, tree ownership and parser recovery.
 * Pins complete trees for distinct grammar shapes and Scala 3.10.1 compiler fixtures;
 * diagnostic matrices also verify that following parameters and declarations survive.
 * PSI accessor contracts and stub/AST transitions are covered by separate suites.
 */
class CaptureSetParametersParserTest extends SimpleScala3ParserTestBase {
  override def scalaCodeParsingFeatures: ScalaFeatures = CaptureCheckingTestUtils.parsingFeatures

  def test_unbounded(): Unit = checkTree(
    checkParseErrors("def f[C^](x: Object^{C}): Unit = ()"),
    """ScalaFile
      |  ScFunctionDefinition: f
      |    AnnotationsList
      |      <empty list>
      |    Modifiers
      |      <empty list>
      |    PsiElement(def)('def')
      |    PsiWhiteSpace(' ')
      |    PsiElement(identifier)('f')
      |    TypeParameterClause
      |      PsiElement([)('[')
      |      TypeParameter: C
      |        PsiElement(identifier)('C')
      |        PsiElement(^)('^')
      |      PsiElement(])(']')
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
      |            CaptureType: Object^{C}
      |              SimpleType: Object
      |                CodeReferenceElement: Object
      |                  PsiElement(identifier)('Object')
      |              PsiElement(^)('^')
      |              CaptureSet
      |                PsiElement({)('{')
      |                CaptureRef
      |                  CodeReferenceElement: C
      |                    PsiElement(identifier)('C')
      |                PsiElement(})('}')
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
      |""".stripMargin
  )

  def test_mixed(): Unit = checkTree(
    checkParseErrors("def f[T, C^, U, D^](x: T): U = ???"),
    """ScalaFile
      |  ScFunctionDefinition: f
      |    AnnotationsList
      |      <empty list>
      |    Modifiers
      |      <empty list>
      |    PsiElement(def)('def')
      |    PsiWhiteSpace(' ')
      |    PsiElement(identifier)('f')
      |    TypeParameterClause
      |      PsiElement([)('[')
      |      TypeParameter: T
      |        PsiElement(identifier)('T')
      |      PsiElement(,)(',')
      |      PsiWhiteSpace(' ')
      |      TypeParameter: C
      |        PsiElement(identifier)('C')
      |        PsiElement(^)('^')
      |      PsiElement(,)(',')
      |      PsiWhiteSpace(' ')
      |      TypeParameter: U
      |        PsiElement(identifier)('U')
      |      PsiElement(,)(',')
      |      PsiWhiteSpace(' ')
      |      TypeParameter: D
      |        PsiElement(identifier)('D')
      |        PsiElement(^)('^')
      |      PsiElement(])(']')
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
      |            SimpleType: T
      |              CodeReferenceElement: T
      |                PsiElement(identifier)('T')
      |        PsiElement())(')')
      |    PsiElement(:)(':')
      |    PsiWhiteSpace(' ')
      |    SimpleType: U
      |      CodeReferenceElement: U
      |        PsiElement(identifier)('U')
      |    PsiWhiteSpace(' ')
      |    PsiElement(=)('=')
      |    PsiWhiteSpace(' ')
      |    ReferenceExpression: ???
      |      PsiElement(identifier)('???')
      |""".stripMargin
  )

  def test_capture_bounds(): Unit = checkTree(
    checkParseErrors("def f[C^ >: {} <: {any}, T](x: Object^{C}): T = ???"),
    """ScalaFile
      |  ScFunctionDefinition: f
      |    AnnotationsList
      |      <empty list>
      |    Modifiers
      |      <empty list>
      |    PsiElement(def)('def')
      |    PsiWhiteSpace(' ')
      |    PsiElement(identifier)('f')
      |    TypeParameterClause
      |      PsiElement([)('[')
      |      TypeParameter: C
      |        PsiElement(identifier)('C')
      |        PsiElement(^)('^')
      |        PsiWhiteSpace(' ')
      |        PsiElement(>:)('>:')
      |        PsiWhiteSpace(' ')
      |        CaptureSet
      |          PsiElement({)('{')
      |          PsiElement(})('}')
      |        PsiWhiteSpace(' ')
      |        PsiElement(<:)('<:')
      |        PsiWhiteSpace(' ')
      |        CaptureSet
      |          PsiElement({)('{')
      |          CaptureRef
      |            CodeReferenceElement: any
      |              PsiElement(identifier)('any')
      |          PsiElement(})('}')
      |      PsiElement(,)(',')
      |      PsiWhiteSpace(' ')
      |      TypeParameter: T
      |        PsiElement(identifier)('T')
      |      PsiElement(])(']')
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
      |            CaptureType: Object^{C}
      |              SimpleType: Object
      |                CodeReferenceElement: Object
      |                  PsiElement(identifier)('Object')
      |              PsiElement(^)('^')
      |              CaptureSet
      |                PsiElement({)('{')
      |                CaptureRef
      |                  CodeReferenceElement: C
      |                    PsiElement(identifier)('C')
      |                PsiElement(})('}')
      |        PsiElement())(')')
      |    PsiElement(:)(':')
      |    PsiWhiteSpace(' ')
      |    SimpleType: T
      |      CodeReferenceElement: T
      |        PsiElement(identifier)('T')
      |    PsiWhiteSpace(' ')
      |    PsiElement(=)('=')
      |    PsiWhiteSpace(' ')
      |    ReferenceExpression: ???
      |      PsiElement(identifier)('???')
      |""".stripMargin
  )

  def test_ordinary_and_capture_bounds(): Unit = checkTree(
    checkParseErrors("""def f[C^ >: {} <: caps.CapSet, T >: Nothing <: { def value: Int }](x: T): Unit = ()
      |def g[C^ >: caps.CapSet <: {any}, T](x: T): Unit = ()
      |""".stripMargin),
    """ScalaFile
      |  ScFunctionDefinition: f
      |    AnnotationsList
      |      <empty list>
      |    Modifiers
      |      <empty list>
      |    PsiElement(def)('def')
      |    PsiWhiteSpace(' ')
      |    PsiElement(identifier)('f')
      |    TypeParameterClause
      |      PsiElement([)('[')
      |      TypeParameter: C
      |        PsiElement(identifier)('C')
      |        PsiElement(^)('^')
      |        PsiWhiteSpace(' ')
      |        PsiElement(>:)('>:')
      |        PsiWhiteSpace(' ')
      |        CaptureSet
      |          PsiElement({)('{')
      |          PsiElement(})('}')
      |        PsiWhiteSpace(' ')
      |        PsiElement(<:)('<:')
      |        PsiWhiteSpace(' ')
      |        SimpleType: caps.CapSet
      |          CodeReferenceElement: caps.CapSet
      |            CodeReferenceElement: caps
      |              PsiElement(identifier)('caps')
      |            PsiElement(.)('.')
      |            PsiElement(identifier)('CapSet')
      |      PsiElement(,)(',')
      |      PsiWhiteSpace(' ')
      |      TypeParameter: T
      |        PsiElement(identifier)('T')
      |        PsiWhiteSpace(' ')
      |        PsiElement(>:)('>:')
      |        PsiWhiteSpace(' ')
      |        SimpleType: Nothing
      |          CodeReferenceElement: Nothing
      |            PsiElement(identifier)('Nothing')
      |        PsiWhiteSpace(' ')
      |        PsiElement(<:)('<:')
      |        PsiWhiteSpace(' ')
      |        CompoundType: { def value: Int }
      |          Refinement
      |            PsiElement({)('{')
      |            PsiWhiteSpace(' ')
      |            ScFunctionDeclaration: value
      |              AnnotationsList
      |                <empty list>
      |              Modifiers
      |                <empty list>
      |              PsiElement(def)('def')
      |              PsiWhiteSpace(' ')
      |              PsiElement(identifier)('value')
      |              Parameters
      |                <empty list>
      |              PsiElement(:)(':')
      |              PsiWhiteSpace(' ')
      |              SimpleType: Int
      |                CodeReferenceElement: Int
      |                  PsiElement(identifier)('Int')
      |            PsiWhiteSpace(' ')
      |            PsiElement(})('}')
      |      PsiElement(])(']')
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
      |            SimpleType: T
      |              CodeReferenceElement: T
      |                PsiElement(identifier)('T')
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
      |  PsiWhiteSpace('\n')
      |  ScFunctionDefinition: g
      |    AnnotationsList
      |      <empty list>
      |    Modifiers
      |      <empty list>
      |    PsiElement(def)('def')
      |    PsiWhiteSpace(' ')
      |    PsiElement(identifier)('g')
      |    TypeParameterClause
      |      PsiElement([)('[')
      |      TypeParameter: C
      |        PsiElement(identifier)('C')
      |        PsiElement(^)('^')
      |        PsiWhiteSpace(' ')
      |        PsiElement(>:)('>:')
      |        PsiWhiteSpace(' ')
      |        SimpleType: caps.CapSet
      |          CodeReferenceElement: caps.CapSet
      |            CodeReferenceElement: caps
      |              PsiElement(identifier)('caps')
      |            PsiElement(.)('.')
      |            PsiElement(identifier)('CapSet')
      |        PsiWhiteSpace(' ')
      |        PsiElement(<:)('<:')
      |        PsiWhiteSpace(' ')
      |        CaptureSet
      |          PsiElement({)('{')
      |          CaptureRef
      |            CodeReferenceElement: any
      |              PsiElement(identifier)('any')
      |          PsiElement(})('}')
      |      PsiElement(,)(',')
      |      PsiWhiteSpace(' ')
      |      TypeParameter: T
      |        PsiElement(identifier)('T')
      |      PsiElement(])(']')
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
      |            SimpleType: T
      |              CodeReferenceElement: T
      |                PsiElement(identifier)('T')
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
      |  PsiWhiteSpace('\n')
      |""".stripMargin
  )

  // Multiple captures in bounds, as in scala/scala3 3.10.1
  // tests/pos-custom-args/captures/cap-paramlists4.scala.
  def test_commas_and_symbolic_names(): Unit = checkTree(
    checkParseErrors("class Box[U^, V^, W^, E^ >: {V, W, +, -, `+`, `-`} <: {U}, F[_]]"),
    """ScalaFile
      |  ScClass: Box
      |    AnnotationsList
      |      <empty list>
      |    Modifiers
      |      <empty list>
      |    PsiElement(class)('class')
      |    PsiWhiteSpace(' ')
      |    PsiElement(identifier)('Box')
      |    TypeParameterClause
      |      PsiElement([)('[')
      |      TypeParameter: U
      |        PsiElement(identifier)('U')
      |        PsiElement(^)('^')
      |      PsiElement(,)(',')
      |      PsiWhiteSpace(' ')
      |      TypeParameter: V
      |        PsiElement(identifier)('V')
      |        PsiElement(^)('^')
      |      PsiElement(,)(',')
      |      PsiWhiteSpace(' ')
      |      TypeParameter: W
      |        PsiElement(identifier)('W')
      |        PsiElement(^)('^')
      |      PsiElement(,)(',')
      |      PsiWhiteSpace(' ')
      |      TypeParameter: E
      |        PsiElement(identifier)('E')
      |        PsiElement(^)('^')
      |        PsiWhiteSpace(' ')
      |        PsiElement(>:)('>:')
      |        PsiWhiteSpace(' ')
      |        CaptureSet
      |          PsiElement({)('{')
      |          CaptureRef
      |            CodeReferenceElement: V
      |              PsiElement(identifier)('V')
      |          PsiElement(,)(',')
      |          PsiWhiteSpace(' ')
      |          CaptureRef
      |            CodeReferenceElement: W
      |              PsiElement(identifier)('W')
      |          PsiElement(,)(',')
      |          PsiWhiteSpace(' ')
      |          CaptureRef
      |            CodeReferenceElement: +
      |              PsiElement(identifier)('+')
      |          PsiElement(,)(',')
      |          PsiWhiteSpace(' ')
      |          CaptureRef
      |            CodeReferenceElement: -
      |              PsiElement(identifier)('-')
      |          PsiElement(,)(',')
      |          PsiWhiteSpace(' ')
      |          CaptureRef
      |            CodeReferenceElement: `+`
      |              PsiElement(identifier)('`+`')
      |          PsiElement(,)(',')
      |          PsiWhiteSpace(' ')
      |          CaptureRef
      |            CodeReferenceElement: `-`
      |              PsiElement(identifier)('`-`')
      |          PsiElement(})('}')
      |        PsiWhiteSpace(' ')
      |        PsiElement(<:)('<:')
      |        PsiWhiteSpace(' ')
      |        CaptureSet
      |          PsiElement({)('{')
      |          CaptureRef
      |            CodeReferenceElement: U
      |              PsiElement(identifier)('U')
      |          PsiElement(})('}')
      |      PsiElement(,)(',')
      |      PsiWhiteSpace(' ')
      |      TypeParameter: F
      |        PsiElement(identifier)('F')
      |        TypeParameterClause
      |          PsiElement([)('[')
      |          TypeParameter: _
      |            PsiElement(_)('_')
      |          PsiElement(])(']')
      |      PsiElement(])(']')
      |    PrimaryConstructor
      |      AnnotationsList
      |        <empty list>
      |      Modifiers
      |        <empty list>
      |      Parameters
      |        <empty list>
      |    ExtendsBlock
      |      <empty list>
      |""".stripMargin
  )

  def test_symbolic_reach(): Unit = checkTree(
    checkParseErrors("""class Box[C^ <: {any, + *}, T]
      |class After""".stripMargin),
    """ScalaFile
      |  ScClass: Box
      |    AnnotationsList
      |      <empty list>
      |    Modifiers
      |      <empty list>
      |    PsiElement(class)('class')
      |    PsiWhiteSpace(' ')
      |    PsiElement(identifier)('Box')
      |    TypeParameterClause
      |      PsiElement([)('[')
      |      TypeParameter: C
      |        PsiElement(identifier)('C')
      |        PsiElement(^)('^')
      |        PsiWhiteSpace(' ')
      |        PsiElement(<:)('<:')
      |        PsiWhiteSpace(' ')
      |        CaptureSet
      |          PsiElement({)('{')
      |          CaptureRef
      |            CodeReferenceElement: any
      |              PsiElement(identifier)('any')
      |          PsiElement(,)(',')
      |          PsiWhiteSpace(' ')
      |          CaptureRef
      |            CodeReferenceElement: +
      |              PsiElement(identifier)('+')
      |            PsiWhiteSpace(' ')
      |            PsiElement(*)('*')
      |          PsiElement(})('}')
      |      PsiElement(,)(',')
      |      PsiWhiteSpace(' ')
      |      TypeParameter: T
      |        PsiElement(identifier)('T')
      |      PsiElement(])(']')
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
      |  ScClass: After
      |    AnnotationsList
      |      <empty list>
      |    Modifiers
      |      <empty list>
      |    PsiElement(class)('class')
      |    PsiWhiteSpace(' ')
      |    PsiElement(identifier)('After')
      |    PrimaryConstructor
      |      AnnotationsList
      |        <empty list>
      |      Modifiers
      |        <empty list>
      |      Parameters
      |        <empty list>
      |    ExtendsBlock
      |      <empty list>
      |""".stripMargin
  )

  def test_filtered_symbolic_reach(): Unit = checkTree(
    checkParseErrors("""class Box[C^ <: {any, + *.only[Capability].rd}, T]
      |class After""".stripMargin),
    """ScalaFile
      |  ScClass: Box
      |    AnnotationsList
      |      <empty list>
      |    Modifiers
      |      <empty list>
      |    PsiElement(class)('class')
      |    PsiWhiteSpace(' ')
      |    PsiElement(identifier)('Box')
      |    TypeParameterClause
      |      PsiElement([)('[')
      |      TypeParameter: C
      |        PsiElement(identifier)('C')
      |        PsiElement(^)('^')
      |        PsiWhiteSpace(' ')
      |        PsiElement(<:)('<:')
      |        PsiWhiteSpace(' ')
      |        CaptureSet
      |          PsiElement({)('{')
      |          CaptureRef
      |            CodeReferenceElement: any
      |              PsiElement(identifier)('any')
      |          PsiElement(,)(',')
      |          PsiWhiteSpace(' ')
      |          CaptureRef
      |            CodeReferenceElement: +
      |              PsiElement(identifier)('+')
      |            PsiWhiteSpace(' ')
      |            PsiElement(*)('*')
      |            CaptureFilter
      |              PsiElement(.)('.')
      |              PsiElement(only)('only')
      |              PsiElement([)('[')
      |              CodeReferenceElement: Capability
      |                PsiElement(identifier)('Capability')
      |              PsiElement(])(']')
      |            PsiElement(.)('.')
      |            PsiElement(rd)('rd')
      |          PsiElement(})('}')
      |      PsiElement(,)(',')
      |      PsiWhiteSpace(' ')
      |      TypeParameter: T
      |        PsiElement(identifier)('T')
      |      PsiElement(])(']')
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
      |  ScClass: After
      |    AnnotationsList
      |      <empty list>
      |    Modifiers
      |      <empty list>
      |    PsiElement(class)('class')
      |    PsiWhiteSpace(' ')
      |    PsiElement(identifier)('After')
      |    PrimaryConstructor
      |      AnnotationsList
      |        <empty list>
      |      Modifiers
      |        <empty list>
      |      Parameters
      |        <empty list>
      |    ExtendsBlock
      |      <empty list>
      |""".stripMargin
  )

  def test_missing_ordinary_lower_bound(): Unit = checkTree(
    checkParseErrors(s"class Box[T >:${err("Wrong type")} <: AnyRef, C^]"),
    """ScalaFile
      |  ScClass: Box
      |    AnnotationsList
      |      <empty list>
      |    Modifiers
      |      <empty list>
      |    PsiElement(class)('class')
      |    PsiWhiteSpace(' ')
      |    PsiElement(identifier)('Box')
      |    TypeParameterClause
      |      PsiElement([)('[')
      |      TypeParameter: T
      |        PsiElement(identifier)('T')
      |        PsiWhiteSpace(' ')
      |        PsiElement(>:)('>:')
      |        PsiErrorElement:Wrong type
      |          <empty list>
      |        PsiWhiteSpace(' ')
      |        PsiElement(<:)('<:')
      |        PsiWhiteSpace(' ')
      |        SimpleType: AnyRef
      |          CodeReferenceElement: AnyRef
      |            PsiElement(identifier)('AnyRef')
      |      PsiElement(,)(',')
      |      PsiWhiteSpace(' ')
      |      TypeParameter: C
      |        PsiElement(identifier)('C')
      |        PsiElement(^)('^')
      |      PsiElement(])(']')
      |    PrimaryConstructor
      |      AnnotationsList
      |        <empty list>
      |      Modifiers
      |        <empty list>
      |      Parameters
      |        <empty list>
      |    ExtendsBlock
      |      <empty list>
      |""".stripMargin
  )

  def test_marker_without_capture_checking(): Unit = checkTree(
    parseScalaFile("def f[C^] = ()", ScalaFeatures.onlyByVersion(ScalaVersion.Latest.Scala_3_8)),
    """ScalaFile
      |  ScFunctionDeclaration: f
      |    AnnotationsList
      |      <empty list>
      |    Modifiers
      |      <empty list>
      |    PsiElement(def)('def')
      |    PsiWhiteSpace(' ')
      |    PsiElement(identifier)('f')
      |    TypeParameterClause
      |      PsiElement([)('[')
      |      TypeParameter: C
      |        PsiElement(identifier)('C')
      |      PsiErrorElement:Wrong parameter
      |        <empty list>
      |    Parameters
      |      <empty list>
      |  ReferenceExpression: ^
      |    PsiElement(identifier)('^')
      |  PsiErrorElement:';' or newline expected
      |    <empty list>
      |  PsiElement(])(']')
      |  PsiErrorElement:Wrong top statement declaration
      |    <empty list>
      |  PsiWhiteSpace(' ')
      |  PsiElement(=)('=')
      |  PsiWhiteSpace(' ')
      |  UnitExpression
      |    PsiElement(()('(')
      |    PsiElement())(')')
      |""".stripMargin
  )

  def test_empty_refinement_without_capture_checking(): Unit = checkTree(
    parseScalaFile("class Box[T <: {}]", ScalaFeatures.onlyByVersion(ScalaVersion.Latest.Scala_3_8)),
    """ScalaFile
      |  ScClass: Box
      |    AnnotationsList
      |      <empty list>
      |    Modifiers
      |      <empty list>
      |    PsiElement(class)('class')
      |    PsiWhiteSpace(' ')
      |    PsiElement(identifier)('Box')
      |    TypeParameterClause
      |      PsiElement([)('[')
      |      TypeParameter: T
      |        PsiElement(identifier)('T')
      |        PsiWhiteSpace(' ')
      |        PsiElement(<:)('<:')
      |        PsiWhiteSpace(' ')
      |        CompoundType: {}
      |          Refinement
      |            PsiElement({)('{')
      |            PsiElement(})('}')
      |      PsiElement(])(']')
      |    PrimaryConstructor
      |      AnnotationsList
      |        <empty list>
      |      Modifiers
      |        <empty list>
      |      Parameters
      |        <empty list>
      |    ExtendsBlock
      |      <empty list>
      |""".stripMargin
  )

  def test_type_lambda(): Unit = checkTree(
    checkParseErrors("type F = [C^] =>> Object^{C}"),
    """ScalaFile
      |  ScTypeAliasDefinition: F
      |    AnnotationsList
      |      <empty list>
      |    Modifiers
      |      <empty list>
      |    PsiElement(type)('type')
      |    PsiWhiteSpace(' ')
      |    PsiElement(identifier)('F')
      |    PsiWhiteSpace(' ')
      |    PsiElement(=)('=')
      |    PsiWhiteSpace(' ')
      |    TypeLambda: [C^] =>> Object^{C}
      |      TypeParameterClause
      |        PsiElement([)('[')
      |        TypeParameter: C
      |          PsiElement(identifier)('C')
      |          PsiElement(^)('^')
      |        PsiElement(])(']')
      |      PsiWhiteSpace(' ')
      |      PsiElement(=>>)('=>>')
      |      PsiWhiteSpace(' ')
      |      CaptureType: Object^{C}
      |        SimpleType: Object
      |          CodeReferenceElement: Object
      |            PsiElement(identifier)('Object')
      |        PsiElement(^)('^')
      |        CaptureSet
      |          PsiElement({)('{')
      |          CaptureRef
      |            CodeReferenceElement: C
      |              PsiElement(identifier)('C')
      |          PsiElement(})('}')
      |""".stripMargin
  )

  def test_polymorphic_function(): Unit = checkTree(
    checkParseErrors("type G = [C^] -> Object^{C}"),
    """ScalaFile
      |  ScTypeAliasDefinition: G
      |    AnnotationsList
      |      <empty list>
      |    Modifiers
      |      <empty list>
      |    PsiElement(type)('type')
      |    PsiWhiteSpace(' ')
      |    PsiElement(identifier)('G')
      |    PsiWhiteSpace(' ')
      |    PsiElement(=)('=')
      |    PsiWhiteSpace(' ')
      |    PolymorphicFunctionType: [C^] -> Object^{C}
      |      TypeParameterClause
      |        PsiElement([)('[')
      |        TypeParameter: C
      |          PsiElement(identifier)('C')
      |          PsiElement(^)('^')
      |        PsiElement(])(']')
      |      PsiWhiteSpace(' ')
      |      PsiElement(->)('->')
      |      PsiWhiteSpace(' ')
      |      CaptureType: Object^{C}
      |        SimpleType: Object
      |          CodeReferenceElement: Object
      |            PsiElement(identifier)('Object')
      |        PsiElement(^)('^')
      |        CaptureSet
      |          PsiElement({)('{')
      |          CaptureRef
      |            CodeReferenceElement: C
      |              PsiElement(identifier)('C')
      |          PsiElement(})('}')
      |""".stripMargin
  )

  def test_variant_capture_parameter(): Unit = checkTree(
    checkParseErrors("class Box[+C^]"),
    """ScalaFile
      |  ScClass: Box
      |    AnnotationsList
      |      <empty list>
      |    Modifiers
      |      <empty list>
      |    PsiElement(class)('class')
      |    PsiWhiteSpace(' ')
      |    PsiElement(identifier)('Box')
      |    TypeParameterClause
      |      PsiElement([)('[')
      |      TypeParameter: C
      |        PsiElement(identifier)('+')
      |        PsiElement(identifier)('C')
      |        PsiElement(^)('^')
      |      PsiElement(])(']')
      |    PrimaryConstructor
      |      AnnotationsList
      |        <empty list>
      |      Modifiers
      |        <empty list>
      |      Parameters
      |        <empty list>
      |    ExtendsBlock
      |      <empty list>
      |""".stripMargin
  )

  def test_missing_upper_type(): Unit = checkTree(
    checkParseErrors(s"""def f[C^ <:${err("Wrong type")} , T](x: Object): Unit = ()
      |def after = ()""".stripMargin),
    """ScalaFile
      |  ScFunctionDefinition: f
      |    AnnotationsList
      |      <empty list>
      |    Modifiers
      |      <empty list>
      |    PsiElement(def)('def')
      |    PsiWhiteSpace(' ')
      |    PsiElement(identifier)('f')
      |    TypeParameterClause
      |      PsiElement([)('[')
      |      TypeParameter: C
      |        PsiElement(identifier)('C')
      |        PsiElement(^)('^')
      |        PsiWhiteSpace(' ')
      |        PsiElement(<:)('<:')
      |        PsiErrorElement:Wrong type
      |          <empty list>
      |      PsiWhiteSpace(' ')
      |      PsiElement(,)(',')
      |      PsiWhiteSpace(' ')
      |      TypeParameter: T
      |        PsiElement(identifier)('T')
      |      PsiElement(])(']')
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
      |            SimpleType: Object
      |              CodeReferenceElement: Object
      |                PsiElement(identifier)('Object')
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
      |  PsiWhiteSpace('\n')
      |  ScFunctionDefinition: after
      |    AnnotationsList
      |      <empty list>
      |    Modifiers
      |      <empty list>
      |    PsiElement(def)('def')
      |    PsiWhiteSpace(' ')
      |    PsiElement(identifier)('after')
      |    Parameters
      |      <empty list>
      |    PsiWhiteSpace(' ')
      |    PsiElement(=)('=')
      |    PsiWhiteSpace(' ')
      |    UnitExpression
      |      PsiElement(()('(')
      |      PsiElement())(')')
      |""".stripMargin
  )

  def test_missing_lower_type(): Unit = checkTree(
    checkParseErrors(s"""def f[C^ >:${err("Wrong type")} <: {any}, T](x: Object): Unit = ()
      |def after = ()""".stripMargin),
    """ScalaFile
      |  ScFunctionDefinition: f
      |    AnnotationsList
      |      <empty list>
      |    Modifiers
      |      <empty list>
      |    PsiElement(def)('def')
      |    PsiWhiteSpace(' ')
      |    PsiElement(identifier)('f')
      |    TypeParameterClause
      |      PsiElement([)('[')
      |      TypeParameter: C
      |        PsiElement(identifier)('C')
      |        PsiElement(^)('^')
      |        PsiWhiteSpace(' ')
      |        PsiElement(>:)('>:')
      |        PsiErrorElement:Wrong type
      |          <empty list>
      |        PsiWhiteSpace(' ')
      |        PsiElement(<:)('<:')
      |        PsiWhiteSpace(' ')
      |        CaptureSet
      |          PsiElement({)('{')
      |          CaptureRef
      |            CodeReferenceElement: any
      |              PsiElement(identifier)('any')
      |          PsiElement(})('}')
      |      PsiElement(,)(',')
      |      PsiWhiteSpace(' ')
      |      TypeParameter: T
      |        PsiElement(identifier)('T')
      |      PsiElement(])(']')
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
      |            SimpleType: Object
      |              CodeReferenceElement: Object
      |                PsiElement(identifier)('Object')
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
      |  PsiWhiteSpace('\n')
      |  ScFunctionDefinition: after
      |    AnnotationsList
      |      <empty list>
      |    Modifiers
      |      <empty list>
      |    PsiElement(def)('def')
      |    PsiWhiteSpace(' ')
      |    PsiElement(identifier)('after')
      |    Parameters
      |      <empty list>
      |    PsiWhiteSpace(' ')
      |    PsiElement(=)('=')
      |    PsiWhiteSpace(' ')
      |    UnitExpression
      |      PsiElement(()('(')
      |      PsiElement())(')')
      |""".stripMargin
  )

  def test_missing_brace_at_list_end(): Unit = checkTree(
    checkParseErrors(s"""def f[C^ <: {any${err("'}' expected")}](x: Object): Unit = ()
      |def after = ()""".stripMargin),
    """ScalaFile
      |  ScFunctionDefinition: f
      |    AnnotationsList
      |      <empty list>
      |    Modifiers
      |      <empty list>
      |    PsiElement(def)('def')
      |    PsiWhiteSpace(' ')
      |    PsiElement(identifier)('f')
      |    TypeParameterClause
      |      PsiElement([)('[')
      |      TypeParameter: C
      |        PsiElement(identifier)('C')
      |        PsiElement(^)('^')
      |        PsiWhiteSpace(' ')
      |        PsiElement(<:)('<:')
      |        PsiWhiteSpace(' ')
      |        CaptureSet
      |          PsiElement({)('{')
      |          CaptureRef
      |            CodeReferenceElement: any
      |              PsiElement(identifier)('any')
      |          PsiErrorElement:'}' expected
      |            <empty list>
      |      PsiElement(])(']')
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
      |            SimpleType: Object
      |              CodeReferenceElement: Object
      |                PsiElement(identifier)('Object')
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
      |  PsiWhiteSpace('\n')
      |  ScFunctionDefinition: after
      |    AnnotationsList
      |      <empty list>
      |    Modifiers
      |      <empty list>
      |    PsiElement(def)('def')
      |    PsiWhiteSpace(' ')
      |    PsiElement(identifier)('after')
      |    Parameters
      |      <empty list>
      |    PsiWhiteSpace(' ')
      |    PsiElement(=)('=')
      |    PsiWhiteSpace(' ')
      |    UnitExpression
      |      PsiElement(()('(')
      |      PsiElement())(')')
      |""".stripMargin
  )

  def test_following_capture_parameter(): Unit = checkTree(
    checkParseErrors(s"""def f[C^ <: {any${err("'}' expected")}, D^](x: Object): Unit = ()
      |def after = ()""".stripMargin),
    """ScalaFile
      |  ScFunctionDefinition: f
      |    AnnotationsList
      |      <empty list>
      |    Modifiers
      |      <empty list>
      |    PsiElement(def)('def')
      |    PsiWhiteSpace(' ')
      |    PsiElement(identifier)('f')
      |    TypeParameterClause
      |      PsiElement([)('[')
      |      TypeParameter: C
      |        PsiElement(identifier)('C')
      |        PsiElement(^)('^')
      |        PsiWhiteSpace(' ')
      |        PsiElement(<:)('<:')
      |        PsiWhiteSpace(' ')
      |        CaptureSet
      |          PsiElement({)('{')
      |          CaptureRef
      |            CodeReferenceElement: any
      |              PsiElement(identifier)('any')
      |          PsiErrorElement:'}' expected
      |            <empty list>
      |      PsiElement(,)(',')
      |      PsiWhiteSpace(' ')
      |      TypeParameter: D
      |        PsiElement(identifier)('D')
      |        PsiElement(^)('^')
      |      PsiElement(])(']')
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
      |            SimpleType: Object
      |              CodeReferenceElement: Object
      |                PsiElement(identifier)('Object')
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
      |  PsiWhiteSpace('\n')
      |  ScFunctionDefinition: after
      |    AnnotationsList
      |      <empty list>
      |    Modifiers
      |      <empty list>
      |    PsiElement(def)('def')
      |    PsiWhiteSpace(' ')
      |    PsiElement(identifier)('after')
      |    Parameters
      |      <empty list>
      |    PsiWhiteSpace(' ')
      |    PsiElement(=)('=')
      |    PsiWhiteSpace(' ')
      |    UnitExpression
      |      PsiElement(()('(')
      |      PsiElement())(')')
      |""".stripMargin
  )

  def test_following_bounded_parameter(): Unit = checkTree(
    checkParseErrors(s"""def f[C^ <: {any${err("'}' expected")}, T <: Object](x: Object): Unit = ()
      |def after = ()""".stripMargin),
    """ScalaFile
      |  ScFunctionDefinition: f
      |    AnnotationsList
      |      <empty list>
      |    Modifiers
      |      <empty list>
      |    PsiElement(def)('def')
      |    PsiWhiteSpace(' ')
      |    PsiElement(identifier)('f')
      |    TypeParameterClause
      |      PsiElement([)('[')
      |      TypeParameter: C
      |        PsiElement(identifier)('C')
      |        PsiElement(^)('^')
      |        PsiWhiteSpace(' ')
      |        PsiElement(<:)('<:')
      |        PsiWhiteSpace(' ')
      |        CaptureSet
      |          PsiElement({)('{')
      |          CaptureRef
      |            CodeReferenceElement: any
      |              PsiElement(identifier)('any')
      |          PsiErrorElement:'}' expected
      |            <empty list>
      |      PsiElement(,)(',')
      |      PsiWhiteSpace(' ')
      |      TypeParameter: T
      |        PsiElement(identifier)('T')
      |        PsiWhiteSpace(' ')
      |        PsiElement(<:)('<:')
      |        PsiWhiteSpace(' ')
      |        SimpleType: Object
      |          CodeReferenceElement: Object
      |            PsiElement(identifier)('Object')
      |      PsiElement(])(']')
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
      |            SimpleType: Object
      |              CodeReferenceElement: Object
      |                PsiElement(identifier)('Object')
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
      |  PsiWhiteSpace('\n')
      |  ScFunctionDefinition: after
      |    AnnotationsList
      |      <empty list>
      |    Modifiers
      |      <empty list>
      |    PsiElement(def)('def')
      |    PsiWhiteSpace(' ')
      |    PsiElement(identifier)('after')
      |    Parameters
      |      <empty list>
      |    PsiWhiteSpace(' ')
      |    PsiElement(=)('=')
      |    PsiWhiteSpace(' ')
      |    UnitExpression
      |      PsiElement(()('(')
      |      PsiElement())(')')
      |""".stripMargin
  )

  def test_following_higher_kinded_parameter(): Unit = checkTree(
    checkParseErrors(s"""def f[C^ <: {any${err("'}' expected")}, F[_]](x: Object): Unit = ()
      |def after = ()""".stripMargin),
    """ScalaFile
      |  ScFunctionDefinition: f
      |    AnnotationsList
      |      <empty list>
      |    Modifiers
      |      <empty list>
      |    PsiElement(def)('def')
      |    PsiWhiteSpace(' ')
      |    PsiElement(identifier)('f')
      |    TypeParameterClause
      |      PsiElement([)('[')
      |      TypeParameter: C
      |        PsiElement(identifier)('C')
      |        PsiElement(^)('^')
      |        PsiWhiteSpace(' ')
      |        PsiElement(<:)('<:')
      |        PsiWhiteSpace(' ')
      |        CaptureSet
      |          PsiElement({)('{')
      |          CaptureRef
      |            CodeReferenceElement: any
      |              PsiElement(identifier)('any')
      |          PsiErrorElement:'}' expected
      |            <empty list>
      |      PsiElement(,)(',')
      |      PsiWhiteSpace(' ')
      |      TypeParameter: F
      |        PsiElement(identifier)('F')
      |        TypeParameterClause
      |          PsiElement([)('[')
      |          TypeParameter: _
      |            PsiElement(_)('_')
      |          PsiElement(])(']')
      |      PsiElement(])(']')
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
      |            SimpleType: Object
      |              CodeReferenceElement: Object
      |                PsiElement(identifier)('Object')
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
      |  PsiWhiteSpace('\n')
      |  ScFunctionDefinition: after
      |    AnnotationsList
      |      <empty list>
      |    Modifiers
      |      <empty list>
      |    PsiElement(def)('def')
      |    PsiWhiteSpace(' ')
      |    PsiElement(identifier)('after')
      |    Parameters
      |      <empty list>
      |    PsiWhiteSpace(' ')
      |    PsiElement(=)('=')
      |    PsiWhiteSpace(' ')
      |    UnitExpression
      |      PsiElement(()('(')
      |      PsiElement())(')')
      |""".stripMargin
  )

  def test_following_context_bound(): Unit = checkTree(
    checkParseErrors(s"""def f[C^ <: {any${err("'}' expected")}, T: Ordering](x: Object): Unit = ()
      |def after = ()""".stripMargin),
    """ScalaFile
      |  ScFunctionDefinition: f
      |    AnnotationsList
      |      <empty list>
      |    Modifiers
      |      <empty list>
      |    PsiElement(def)('def')
      |    PsiWhiteSpace(' ')
      |    PsiElement(identifier)('f')
      |    TypeParameterClause
      |      PsiElement([)('[')
      |      TypeParameter: C
      |        PsiElement(identifier)('C')
      |        PsiElement(^)('^')
      |        PsiWhiteSpace(' ')
      |        PsiElement(<:)('<:')
      |        PsiWhiteSpace(' ')
      |        CaptureSet
      |          PsiElement({)('{')
      |          CaptureRef
      |            CodeReferenceElement: any
      |              PsiElement(identifier)('any')
      |          PsiErrorElement:'}' expected
      |            <empty list>
      |      PsiElement(,)(',')
      |      PsiWhiteSpace(' ')
      |      TypeParameter: T
      |        PsiElement(identifier)('T')
      |        PsiElement(:)(':')
      |        PsiWhiteSpace(' ')
      |        ScContextBoundImpl(context bound)
      |          SimpleType: Ordering
      |            CodeReferenceElement: Ordering
      |              PsiElement(identifier)('Ordering')
      |      PsiElement(])(']')
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
      |            SimpleType: Object
      |              CodeReferenceElement: Object
      |                PsiElement(identifier)('Object')
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
      |  PsiWhiteSpace('\n')
      |  ScFunctionDefinition: after
      |    AnnotationsList
      |      <empty list>
      |    Modifiers
      |      <empty list>
      |    PsiElement(def)('def')
      |    PsiWhiteSpace(' ')
      |    PsiElement(identifier)('after')
      |    Parameters
      |      <empty list>
      |    PsiWhiteSpace(' ')
      |    PsiElement(=)('=')
      |    PsiWhiteSpace(' ')
      |    UnitExpression
      |      PsiElement(()('(')
      |      PsiElement())(')')
      |""".stripMargin
  )

  def test_incomplete_filter_argument(): Unit = checkTree(
    checkParseErrors(s"""def f[C^ <: {any.except[${err("Wrong qualified identifier")}
      |]${err("'}' expected")}, D^](x: Object): Unit = ()
      |def after = ()""".stripMargin),
    """ScalaFile
      |  ScFunctionDefinition: f
      |    AnnotationsList
      |      <empty list>
      |    Modifiers
      |      <empty list>
      |    PsiElement(def)('def')
      |    PsiWhiteSpace(' ')
      |    PsiElement(identifier)('f')
      |    TypeParameterClause
      |      PsiElement([)('[')
      |      TypeParameter: C
      |        PsiElement(identifier)('C')
      |        PsiElement(^)('^')
      |        PsiWhiteSpace(' ')
      |        PsiElement(<:)('<:')
      |        PsiWhiteSpace(' ')
      |        CaptureSet
      |          PsiElement({)('{')
      |          CaptureRef
      |            CodeReferenceElement: any
      |              PsiElement(identifier)('any')
      |            PsiElement(.)('.')
      |            PsiElement(except)('except')
      |            PsiElement([)('[')
      |            PsiErrorElement:Wrong qualified identifier
      |              <empty list>
      |            PsiWhiteSpace('\n')
      |            PsiElement(])(']')
      |          PsiErrorElement:'}' expected
      |            <empty list>
      |      PsiElement(,)(',')
      |      PsiWhiteSpace(' ')
      |      TypeParameter: D
      |        PsiElement(identifier)('D')
      |        PsiElement(^)('^')
      |      PsiElement(])(']')
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
      |            SimpleType: Object
      |              CodeReferenceElement: Object
      |                PsiElement(identifier)('Object')
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
      |  PsiWhiteSpace('\n')
      |  ScFunctionDefinition: after
      |    AnnotationsList
      |      <empty list>
      |    Modifiers
      |      <empty list>
      |    PsiElement(def)('def')
      |    PsiWhiteSpace(' ')
      |    PsiElement(identifier)('after')
      |    Parameters
      |      <empty list>
      |    PsiWhiteSpace(' ')
      |    PsiElement(=)('=')
      |    PsiWhiteSpace(' ')
      |    UnitExpression
      |      PsiElement(()('(')
      |      PsiElement())(')')
      |""".stripMargin
  )

  def test_variant_complete(): Unit = checkTree(
    checkParseErrors("""class Box[C^ <: {any}, +T]
      |class After""".stripMargin),
    """ScalaFile
      |  ScClass: Box
      |    AnnotationsList
      |      <empty list>
      |    Modifiers
      |      <empty list>
      |    PsiElement(class)('class')
      |    PsiWhiteSpace(' ')
      |    PsiElement(identifier)('Box')
      |    TypeParameterClause
      |      PsiElement([)('[')
      |      TypeParameter: C
      |        PsiElement(identifier)('C')
      |        PsiElement(^)('^')
      |        PsiWhiteSpace(' ')
      |        PsiElement(<:)('<:')
      |        PsiWhiteSpace(' ')
      |        CaptureSet
      |          PsiElement({)('{')
      |          CaptureRef
      |            CodeReferenceElement: any
      |              PsiElement(identifier)('any')
      |          PsiElement(})('}')
      |      PsiElement(,)(',')
      |      PsiWhiteSpace(' ')
      |      TypeParameter: T
      |        PsiElement(identifier)('+')
      |        PsiElement(identifier)('T')
      |      PsiElement(])(']')
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
      |  ScClass: After
      |    AnnotationsList
      |      <empty list>
      |    Modifiers
      |      <empty list>
      |    PsiElement(class)('class')
      |    PsiWhiteSpace(' ')
      |    PsiElement(identifier)('After')
      |    PrimaryConstructor
      |      AnnotationsList
      |        <empty list>
      |      Modifiers
      |        <empty list>
      |      Parameters
      |        <empty list>
      |    ExtendsBlock
      |      <empty list>
      |""".stripMargin
  )

  def test_variant_incomplete(): Unit = checkTree(
    checkParseErrors(s"""class Box[C^ <: {any${err("'}' expected")}, +T]
      |class After""".stripMargin),
    """ScalaFile
      |  ScClass: Box
      |    AnnotationsList
      |      <empty list>
      |    Modifiers
      |      <empty list>
      |    PsiElement(class)('class')
      |    PsiWhiteSpace(' ')
      |    PsiElement(identifier)('Box')
      |    TypeParameterClause
      |      PsiElement([)('[')
      |      TypeParameter: C
      |        PsiElement(identifier)('C')
      |        PsiElement(^)('^')
      |        PsiWhiteSpace(' ')
      |        PsiElement(<:)('<:')
      |        PsiWhiteSpace(' ')
      |        CaptureSet
      |          PsiElement({)('{')
      |          CaptureRef
      |            CodeReferenceElement: any
      |              PsiElement(identifier)('any')
      |          PsiErrorElement:'}' expected
      |            <empty list>
      |      PsiElement(,)(',')
      |      PsiWhiteSpace(' ')
      |      TypeParameter: T
      |        PsiElement(identifier)('+')
      |        PsiElement(identifier)('T')
      |      PsiElement(])(']')
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
      |  ScClass: After
      |    AnnotationsList
      |      <empty list>
      |    Modifiers
      |      <empty list>
      |    PsiElement(class)('class')
      |    PsiWhiteSpace(' ')
      |    PsiElement(identifier)('After')
      |    PrimaryConstructor
      |      AnnotationsList
      |        <empty list>
      |      Modifiers
      |        <empty list>
      |      Parameters
      |        <empty list>
      |    ExtendsBlock
      |      <empty list>
      |""".stripMargin
  )

  def test_backticked_star_complete(): Unit = checkTree(
    checkParseErrors("""class Box[C^ <: {any}, +`*`]
      |class After""".stripMargin),
    """ScalaFile
      |  ScClass: Box
      |    AnnotationsList
      |      <empty list>
      |    Modifiers
      |      <empty list>
      |    PsiElement(class)('class')
      |    PsiWhiteSpace(' ')
      |    PsiElement(identifier)('Box')
      |    TypeParameterClause
      |      PsiElement([)('[')
      |      TypeParameter: C
      |        PsiElement(identifier)('C')
      |        PsiElement(^)('^')
      |        PsiWhiteSpace(' ')
      |        PsiElement(<:)('<:')
      |        PsiWhiteSpace(' ')
      |        CaptureSet
      |          PsiElement({)('{')
      |          CaptureRef
      |            CodeReferenceElement: any
      |              PsiElement(identifier)('any')
      |          PsiElement(})('}')
      |      PsiElement(,)(',')
      |      PsiWhiteSpace(' ')
      |      TypeParameter: `*`
      |        PsiElement(identifier)('+')
      |        PsiElement(identifier)('`*`')
      |      PsiElement(])(']')
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
      |  ScClass: After
      |    AnnotationsList
      |      <empty list>
      |    Modifiers
      |      <empty list>
      |    PsiElement(class)('class')
      |    PsiWhiteSpace(' ')
      |    PsiElement(identifier)('After')
      |    PrimaryConstructor
      |      AnnotationsList
      |        <empty list>
      |      Modifiers
      |        <empty list>
      |      Parameters
      |        <empty list>
      |    ExtendsBlock
      |      <empty list>
      |""".stripMargin
  )

  def test_backticked_star_incomplete(): Unit = checkTree(
    checkParseErrors(s"""class Box[C^ <: {any${err("'}' expected")}, +`*`]
      |class After""".stripMargin),
    """ScalaFile
      |  ScClass: Box
      |    AnnotationsList
      |      <empty list>
      |    Modifiers
      |      <empty list>
      |    PsiElement(class)('class')
      |    PsiWhiteSpace(' ')
      |    PsiElement(identifier)('Box')
      |    TypeParameterClause
      |      PsiElement([)('[')
      |      TypeParameter: C
      |        PsiElement(identifier)('C')
      |        PsiElement(^)('^')
      |        PsiWhiteSpace(' ')
      |        PsiElement(<:)('<:')
      |        PsiWhiteSpace(' ')
      |        CaptureSet
      |          PsiElement({)('{')
      |          CaptureRef
      |            CodeReferenceElement: any
      |              PsiElement(identifier)('any')
      |          PsiErrorElement:'}' expected
      |            <empty list>
      |      PsiElement(,)(',')
      |      PsiWhiteSpace(' ')
      |      TypeParameter: `*`
      |        PsiElement(identifier)('+')
      |        PsiElement(identifier)('`*`')
      |      PsiElement(])(']')
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
      |  ScClass: After
      |    AnnotationsList
      |      <empty list>
      |    Modifiers
      |      <empty list>
      |    PsiElement(class)('class')
      |    PsiWhiteSpace(' ')
      |    PsiElement(identifier)('After')
      |    PrimaryConstructor
      |      AnnotationsList
      |        <empty list>
      |      Modifiers
      |        <empty list>
      |      Parameters
      |        <empty list>
      |    ExtendsBlock
      |      <empty list>
      |""".stripMargin
  )

  def test_annotated_complete(): Unit = checkTree(
    checkParseErrors("""class Box[C^ <: {any}, @ann T]
      |class After""".stripMargin),
    """ScalaFile
      |  ScClass: Box
      |    AnnotationsList
      |      <empty list>
      |    Modifiers
      |      <empty list>
      |    PsiElement(class)('class')
      |    PsiWhiteSpace(' ')
      |    PsiElement(identifier)('Box')
      |    TypeParameterClause
      |      PsiElement([)('[')
      |      TypeParameter: C
      |        PsiElement(identifier)('C')
      |        PsiElement(^)('^')
      |        PsiWhiteSpace(' ')
      |        PsiElement(<:)('<:')
      |        PsiWhiteSpace(' ')
      |        CaptureSet
      |          PsiElement({)('{')
      |          CaptureRef
      |            CodeReferenceElement: any
      |              PsiElement(identifier)('any')
      |          PsiElement(})('}')
      |      PsiElement(,)(',')
      |      PsiWhiteSpace(' ')
      |      TypeParameter: T
      |        AnnotationsList
      |          Annotation
      |            PsiElement(@)('@')
      |            AnnotationExpression
      |              ConstructorInvocation
      |                SimpleType: ann
      |                  CodeReferenceElement: ann
      |                    PsiElement(identifier)('ann')
      |        PsiWhiteSpace(' ')
      |        PsiElement(identifier)('T')
      |      PsiElement(])(']')
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
      |  ScClass: After
      |    AnnotationsList
      |      <empty list>
      |    Modifiers
      |      <empty list>
      |    PsiElement(class)('class')
      |    PsiWhiteSpace(' ')
      |    PsiElement(identifier)('After')
      |    PrimaryConstructor
      |      AnnotationsList
      |        <empty list>
      |      Modifiers
      |        <empty list>
      |      Parameters
      |        <empty list>
      |    ExtendsBlock
      |      <empty list>
      |""".stripMargin
  )

  def test_annotated_incomplete(): Unit = checkTree(
    checkParseErrors(s"""class Box[C^ <: {any${err("'}' expected")}, @ann T]
      |class After""".stripMargin),
    """ScalaFile
      |  ScClass: Box
      |    AnnotationsList
      |      <empty list>
      |    Modifiers
      |      <empty list>
      |    PsiElement(class)('class')
      |    PsiWhiteSpace(' ')
      |    PsiElement(identifier)('Box')
      |    TypeParameterClause
      |      PsiElement([)('[')
      |      TypeParameter: C
      |        PsiElement(identifier)('C')
      |        PsiElement(^)('^')
      |        PsiWhiteSpace(' ')
      |        PsiElement(<:)('<:')
      |        PsiWhiteSpace(' ')
      |        CaptureSet
      |          PsiElement({)('{')
      |          CaptureRef
      |            CodeReferenceElement: any
      |              PsiElement(identifier)('any')
      |          PsiErrorElement:'}' expected
      |            <empty list>
      |      PsiElement(,)(',')
      |      PsiWhiteSpace(' ')
      |      TypeParameter: T
      |        AnnotationsList
      |          Annotation
      |            PsiElement(@)('@')
      |            AnnotationExpression
      |              ConstructorInvocation
      |                SimpleType: ann
      |                  CodeReferenceElement: ann
      |                    PsiElement(identifier)('ann')
      |        PsiWhiteSpace(' ')
      |        PsiElement(identifier)('T')
      |      PsiElement(])(']')
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
      |  ScClass: After
      |    AnnotationsList
      |      <empty list>
      |    Modifiers
      |      <empty list>
      |    PsiElement(class)('class')
      |    PsiWhiteSpace(' ')
      |    PsiElement(identifier)('After')
      |    PrimaryConstructor
      |      AnnotationsList
      |        <empty list>
      |      Modifiers
      |        <empty list>
      |      Parameters
      |        <empty list>
      |    ExtendsBlock
      |      <empty list>
      |""".stripMargin
  )

  def test_annotated_variant_complete(): Unit = checkTree(
    checkParseErrors("""class Box[C^ <: {any}, @ann +T]
      |class After""".stripMargin),
    """ScalaFile
      |  ScClass: Box
      |    AnnotationsList
      |      <empty list>
      |    Modifiers
      |      <empty list>
      |    PsiElement(class)('class')
      |    PsiWhiteSpace(' ')
      |    PsiElement(identifier)('Box')
      |    TypeParameterClause
      |      PsiElement([)('[')
      |      TypeParameter: C
      |        PsiElement(identifier)('C')
      |        PsiElement(^)('^')
      |        PsiWhiteSpace(' ')
      |        PsiElement(<:)('<:')
      |        PsiWhiteSpace(' ')
      |        CaptureSet
      |          PsiElement({)('{')
      |          CaptureRef
      |            CodeReferenceElement: any
      |              PsiElement(identifier)('any')
      |          PsiElement(})('}')
      |      PsiElement(,)(',')
      |      PsiWhiteSpace(' ')
      |      TypeParameter: T
      |        AnnotationsList
      |          Annotation
      |            PsiElement(@)('@')
      |            AnnotationExpression
      |              ConstructorInvocation
      |                SimpleType: ann
      |                  CodeReferenceElement: ann
      |                    PsiElement(identifier)('ann')
      |        PsiWhiteSpace(' ')
      |        PsiElement(identifier)('+')
      |        PsiElement(identifier)('T')
      |      PsiElement(])(']')
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
      |  ScClass: After
      |    AnnotationsList
      |      <empty list>
      |    Modifiers
      |      <empty list>
      |    PsiElement(class)('class')
      |    PsiWhiteSpace(' ')
      |    PsiElement(identifier)('After')
      |    PrimaryConstructor
      |      AnnotationsList
      |        <empty list>
      |      Modifiers
      |        <empty list>
      |      Parameters
      |        <empty list>
      |    ExtendsBlock
      |      <empty list>
      |""".stripMargin
  )

  def test_annotated_variant_incomplete(): Unit = checkTree(
    checkParseErrors(s"""class Box[C^ <: {any${err("'}' expected")}, @ann +T]
      |class After""".stripMargin),
    """ScalaFile
      |  ScClass: Box
      |    AnnotationsList
      |      <empty list>
      |    Modifiers
      |      <empty list>
      |    PsiElement(class)('class')
      |    PsiWhiteSpace(' ')
      |    PsiElement(identifier)('Box')
      |    TypeParameterClause
      |      PsiElement([)('[')
      |      TypeParameter: C
      |        PsiElement(identifier)('C')
      |        PsiElement(^)('^')
      |        PsiWhiteSpace(' ')
      |        PsiElement(<:)('<:')
      |        PsiWhiteSpace(' ')
      |        CaptureSet
      |          PsiElement({)('{')
      |          CaptureRef
      |            CodeReferenceElement: any
      |              PsiElement(identifier)('any')
      |          PsiErrorElement:'}' expected
      |            <empty list>
      |      PsiElement(,)(',')
      |      PsiWhiteSpace(' ')
      |      TypeParameter: T
      |        AnnotationsList
      |          Annotation
      |            PsiElement(@)('@')
      |            AnnotationExpression
      |              ConstructorInvocation
      |                SimpleType: ann
      |                  CodeReferenceElement: ann
      |                    PsiElement(identifier)('ann')
      |        PsiWhiteSpace(' ')
      |        PsiElement(identifier)('+')
      |        PsiElement(identifier)('T')
      |      PsiElement(])(']')
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
      |  ScClass: After
      |    AnnotationsList
      |      <empty list>
      |    Modifiers
      |      <empty list>
      |    PsiElement(class)('class')
      |    PsiWhiteSpace(' ')
      |    PsiElement(identifier)('After')
      |    PrimaryConstructor
      |      AnnotationsList
      |        <empty list>
      |      Modifiers
      |        <empty list>
      |      Parameters
      |        <empty list>
      |    ExtendsBlock
      |      <empty list>
      |""".stripMargin
  )

  def test_multiple_annotations_complete(): Unit = checkTree(
    checkParseErrors("""class Box[C^ <: {any}, @ann(1) @other -T]
      |class After""".stripMargin),
    """ScalaFile
      |  ScClass: Box
      |    AnnotationsList
      |      <empty list>
      |    Modifiers
      |      <empty list>
      |    PsiElement(class)('class')
      |    PsiWhiteSpace(' ')
      |    PsiElement(identifier)('Box')
      |    TypeParameterClause
      |      PsiElement([)('[')
      |      TypeParameter: C
      |        PsiElement(identifier)('C')
      |        PsiElement(^)('^')
      |        PsiWhiteSpace(' ')
      |        PsiElement(<:)('<:')
      |        PsiWhiteSpace(' ')
      |        CaptureSet
      |          PsiElement({)('{')
      |          CaptureRef
      |            CodeReferenceElement: any
      |              PsiElement(identifier)('any')
      |          PsiElement(})('}')
      |      PsiElement(,)(',')
      |      PsiWhiteSpace(' ')
      |      TypeParameter: T
      |        AnnotationsList
      |          Annotation
      |            PsiElement(@)('@')
      |            AnnotationExpression
      |              ConstructorInvocation
      |                SimpleType: ann
      |                  CodeReferenceElement: ann
      |                    PsiElement(identifier)('ann')
      |                ArgumentList
      |                  PsiElement(()('(')
      |                  IntegerLiteral
      |                    PsiElement(integer)('1')
      |                  PsiElement())(')')
      |          PsiWhiteSpace(' ')
      |          Annotation
      |            PsiElement(@)('@')
      |            AnnotationExpression
      |              ConstructorInvocation
      |                SimpleType: other
      |                  CodeReferenceElement: other
      |                    PsiElement(identifier)('other')
      |        PsiWhiteSpace(' ')
      |        PsiElement(identifier)('-')
      |        PsiElement(identifier)('T')
      |      PsiElement(])(']')
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
      |  ScClass: After
      |    AnnotationsList
      |      <empty list>
      |    Modifiers
      |      <empty list>
      |    PsiElement(class)('class')
      |    PsiWhiteSpace(' ')
      |    PsiElement(identifier)('After')
      |    PrimaryConstructor
      |      AnnotationsList
      |        <empty list>
      |      Modifiers
      |        <empty list>
      |      Parameters
      |        <empty list>
      |    ExtendsBlock
      |      <empty list>
      |""".stripMargin
  )

  def test_multiple_annotations_incomplete(): Unit = checkTree(
    checkParseErrors(s"""class Box[C^ <: {any${err("'}' expected")}, @ann(1) @other -T]
      |class After""".stripMargin),
    """ScalaFile
      |  ScClass: Box
      |    AnnotationsList
      |      <empty list>
      |    Modifiers
      |      <empty list>
      |    PsiElement(class)('class')
      |    PsiWhiteSpace(' ')
      |    PsiElement(identifier)('Box')
      |    TypeParameterClause
      |      PsiElement([)('[')
      |      TypeParameter: C
      |        PsiElement(identifier)('C')
      |        PsiElement(^)('^')
      |        PsiWhiteSpace(' ')
      |        PsiElement(<:)('<:')
      |        PsiWhiteSpace(' ')
      |        CaptureSet
      |          PsiElement({)('{')
      |          CaptureRef
      |            CodeReferenceElement: any
      |              PsiElement(identifier)('any')
      |          PsiErrorElement:'}' expected
      |            <empty list>
      |      PsiElement(,)(',')
      |      PsiWhiteSpace(' ')
      |      TypeParameter: T
      |        AnnotationsList
      |          Annotation
      |            PsiElement(@)('@')
      |            AnnotationExpression
      |              ConstructorInvocation
      |                SimpleType: ann
      |                  CodeReferenceElement: ann
      |                    PsiElement(identifier)('ann')
      |                ArgumentList
      |                  PsiElement(()('(')
      |                  IntegerLiteral
      |                    PsiElement(integer)('1')
      |                  PsiElement())(')')
      |          PsiWhiteSpace(' ')
      |          Annotation
      |            PsiElement(@)('@')
      |            AnnotationExpression
      |              ConstructorInvocation
      |                SimpleType: other
      |                  CodeReferenceElement: other
      |                    PsiElement(identifier)('other')
      |        PsiWhiteSpace(' ')
      |        PsiElement(identifier)('-')
      |        PsiElement(identifier)('T')
      |      PsiElement(])(']')
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
      |  ScClass: After
      |    AnnotationsList
      |      <empty list>
      |    Modifiers
      |      <empty list>
      |    PsiElement(class)('class')
      |    PsiWhiteSpace(' ')
      |    PsiElement(identifier)('After')
      |    PrimaryConstructor
      |      AnnotationsList
      |        <empty list>
      |      Modifiers
      |        <empty list>
      |      Parameters
      |        <empty list>
      |    ExtendsBlock
      |      <empty list>
      |""".stripMargin
  )

  def test_same_line_trailing_comma(): Unit = checkTree(
    checkParseErrors(s"""class Box[C^ <: {any,${err("Capture reference expected")}}, T]
      |class After""".stripMargin),
    """ScalaFile
      |  ScClass: Box
      |    AnnotationsList
      |      <empty list>
      |    Modifiers
      |      <empty list>
      |    PsiElement(class)('class')
      |    PsiWhiteSpace(' ')
      |    PsiElement(identifier)('Box')
      |    TypeParameterClause
      |      PsiElement([)('[')
      |      TypeParameter: C
      |        PsiElement(identifier)('C')
      |        PsiElement(^)('^')
      |        PsiWhiteSpace(' ')
      |        PsiElement(<:)('<:')
      |        PsiWhiteSpace(' ')
      |        CaptureSet
      |          PsiElement({)('{')
      |          CaptureRef
      |            CodeReferenceElement: any
      |              PsiElement(identifier)('any')
      |          PsiElement(,)(',')
      |          PsiErrorElement:Capture reference expected
      |            <empty list>
      |          PsiElement(})('}')
      |      PsiElement(,)(',')
      |      PsiWhiteSpace(' ')
      |      TypeParameter: T
      |        PsiElement(identifier)('T')
      |      PsiElement(])(']')
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
      |  ScClass: After
      |    AnnotationsList
      |      <empty list>
      |    Modifiers
      |      <empty list>
      |    PsiElement(class)('class')
      |    PsiWhiteSpace(' ')
      |    PsiElement(identifier)('After')
      |    PrimaryConstructor
      |      AnnotationsList
      |        <empty list>
      |      Modifiers
      |        <empty list>
      |      Parameters
      |        <empty list>
      |    ExtendsBlock
      |      <empty list>
      |""".stripMargin
  )

  def test_multiline_trailing_comma(): Unit = checkTree(
    checkParseErrors("""class Box[C^ <: {any,
      |}, T]
      |class After""".stripMargin),
    """ScalaFile
      |  ScClass: Box
      |    AnnotationsList
      |      <empty list>
      |    Modifiers
      |      <empty list>
      |    PsiElement(class)('class')
      |    PsiWhiteSpace(' ')
      |    PsiElement(identifier)('Box')
      |    TypeParameterClause
      |      PsiElement([)('[')
      |      TypeParameter: C
      |        PsiElement(identifier)('C')
      |        PsiElement(^)('^')
      |        PsiWhiteSpace(' ')
      |        PsiElement(<:)('<:')
      |        PsiWhiteSpace(' ')
      |        CaptureSet
      |          PsiElement({)('{')
      |          CaptureRef
      |            CodeReferenceElement: any
      |              PsiElement(identifier)('any')
      |          PsiElement(,)(',')
      |          PsiWhiteSpace('\n')
      |          PsiElement(})('}')
      |      PsiElement(,)(',')
      |      PsiWhiteSpace(' ')
      |      TypeParameter: T
      |        PsiElement(identifier)('T')
      |      PsiElement(])(']')
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
      |  ScClass: After
      |    AnnotationsList
      |      <empty list>
      |    Modifiers
      |      <empty list>
      |    PsiElement(class)('class')
      |    PsiWhiteSpace(' ')
      |    PsiElement(identifier)('After')
      |    PrimaryConstructor
      |      AnnotationsList
      |        <empty list>
      |      Modifiers
      |        <empty list>
      |      Parameters
      |        <empty list>
      |    ExtendsBlock
      |      <empty list>
      |""".stripMargin
  )

  def test_unfinished_parameter_list(): Unit = checkTree(
    checkParseErrors(s"""class Box[C^${err("']' expected")}
      |class After""".stripMargin),
    """ScalaFile
      |  ScClass: Box
      |    AnnotationsList
      |      <empty list>
      |    Modifiers
      |      <empty list>
      |    PsiElement(class)('class')
      |    PsiWhiteSpace(' ')
      |    PsiElement(identifier)('Box')
      |    TypeParameterClause
      |      PsiElement([)('[')
      |      TypeParameter: C
      |        PsiElement(identifier)('C')
      |        PsiElement(^)('^')
      |      PsiErrorElement:']' expected
      |        <empty list>
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
      |  ScClass: After
      |    AnnotationsList
      |      <empty list>
      |    Modifiers
      |      <empty list>
      |    PsiElement(class)('class')
      |    PsiWhiteSpace(' ')
      |    PsiElement(identifier)('After')
      |    PrimaryConstructor
      |      AnnotationsList
      |        <empty list>
      |      Modifiers
      |        <empty list>
      |      Parameters
      |        <empty list>
      |    ExtendsBlock
      |      <empty list>
      |""".stripMargin
  )

  // PsiBuilder reports only the first diagnostic at the same offset:
  // the missing capture-set brace precedes the missing parameter-list bracket.
  def test_unfinished_bound_and_parameter_list(): Unit = checkTree(
    checkParseErrors(s"""class Box[C^ <: {any${err("'}' expected")}
      |class After""".stripMargin),
    """ScalaFile
      |  ScClass: Box
      |    AnnotationsList
      |      <empty list>
      |    Modifiers
      |      <empty list>
      |    PsiElement(class)('class')
      |    PsiWhiteSpace(' ')
      |    PsiElement(identifier)('Box')
      |    TypeParameterClause
      |      PsiElement([)('[')
      |      TypeParameter: C
      |        PsiElement(identifier)('C')
      |        PsiElement(^)('^')
      |        PsiWhiteSpace(' ')
      |        PsiElement(<:)('<:')
      |        PsiWhiteSpace(' ')
      |        CaptureSet
      |          PsiElement({)('{')
      |          CaptureRef
      |            CodeReferenceElement: any
      |              PsiElement(identifier)('any')
      |          PsiErrorElement:'}' expected
      |            <empty list>
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
      |  ScClass: After
      |    AnnotationsList
      |      <empty list>
      |    Modifiers
      |      <empty list>
      |    PsiElement(class)('class')
      |    PsiWhiteSpace(' ')
      |    PsiElement(identifier)('After')
      |    PrimaryConstructor
      |      AnnotationsList
      |        <empty list>
      |      Modifiers
      |        <empty list>
      |      Parameters
      |        <empty list>
      |    ExtendsBlock
      |      <empty list>
      |""".stripMargin
  )

  def test_abstract_alias_bounds(): Unit = checkTree(
    checkParseErrors("trait Box[U^, V^, W^] { type Both >: {V,W} <: {U} }"),
    """ScalaFile
      |  ScTrait: Box
      |    AnnotationsList
      |      <empty list>
      |    Modifiers
      |      <empty list>
      |    PsiElement(trait)('trait')
      |    PsiWhiteSpace(' ')
      |    PsiElement(identifier)('Box')
      |    TypeParameterClause
      |      PsiElement([)('[')
      |      TypeParameter: U
      |        PsiElement(identifier)('U')
      |        PsiElement(^)('^')
      |      PsiElement(,)(',')
      |      PsiWhiteSpace(' ')
      |      TypeParameter: V
      |        PsiElement(identifier)('V')
      |        PsiElement(^)('^')
      |      PsiElement(,)(',')
      |      PsiWhiteSpace(' ')
      |      TypeParameter: W
      |        PsiElement(identifier)('W')
      |        PsiElement(^)('^')
      |      PsiElement(])(']')
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
      |        PsiWhiteSpace(' ')
      |        ScTypeAliasDeclaration: Both
      |          AnnotationsList
      |            <empty list>
      |          Modifiers
      |            <empty list>
      |          PsiElement(type)('type')
      |          PsiWhiteSpace(' ')
      |          PsiElement(identifier)('Both')
      |          PsiWhiteSpace(' ')
      |          PsiElement(>:)('>:')
      |          PsiWhiteSpace(' ')
      |          CaptureSet
      |            PsiElement({)('{')
      |            CaptureRef
      |              CodeReferenceElement: V
      |                PsiElement(identifier)('V')
      |            PsiElement(,)(',')
      |            CaptureRef
      |              CodeReferenceElement: W
      |                PsiElement(identifier)('W')
      |            PsiElement(})('}')
      |          PsiWhiteSpace(' ')
      |          PsiElement(<:)('<:')
      |          PsiWhiteSpace(' ')
      |          CaptureSet
      |            PsiElement({)('{')
      |            CaptureRef
      |              CodeReferenceElement: U
      |                PsiElement(identifier)('U')
      |            PsiElement(})('}')
      |        PsiWhiteSpace(' ')
      |        PsiElement(})('}')
      |""".stripMargin
  )

  def test_opaque_alias_bounds(): Unit = checkTree(
    checkParseErrors("trait Box[U^, V^, W^] { opaque type Both >: {V,W} <: {U} = caps.CapSet }"),
    """ScalaFile
      |  ScTrait: Box
      |    AnnotationsList
      |      <empty list>
      |    Modifiers
      |      <empty list>
      |    PsiElement(trait)('trait')
      |    PsiWhiteSpace(' ')
      |    PsiElement(identifier)('Box')
      |    TypeParameterClause
      |      PsiElement([)('[')
      |      TypeParameter: U
      |        PsiElement(identifier)('U')
      |        PsiElement(^)('^')
      |      PsiElement(,)(',')
      |      PsiWhiteSpace(' ')
      |      TypeParameter: V
      |        PsiElement(identifier)('V')
      |        PsiElement(^)('^')
      |      PsiElement(,)(',')
      |      PsiWhiteSpace(' ')
      |      TypeParameter: W
      |        PsiElement(identifier)('W')
      |        PsiElement(^)('^')
      |      PsiElement(])(']')
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
      |        PsiWhiteSpace(' ')
      |        ScTypeAliasDefinition: Both
      |          AnnotationsList
      |            <empty list>
      |          Modifiers
      |            PsiElement(opaque)('opaque')
      |          PsiWhiteSpace(' ')
      |          PsiElement(type)('type')
      |          PsiWhiteSpace(' ')
      |          PsiElement(identifier)('Both')
      |          PsiWhiteSpace(' ')
      |          PsiElement(>:)('>:')
      |          PsiWhiteSpace(' ')
      |          CaptureSet
      |            PsiElement({)('{')
      |            CaptureRef
      |              CodeReferenceElement: V
      |                PsiElement(identifier)('V')
      |            PsiElement(,)(',')
      |            CaptureRef
      |              CodeReferenceElement: W
      |                PsiElement(identifier)('W')
      |            PsiElement(})('}')
      |          PsiWhiteSpace(' ')
      |          PsiElement(<:)('<:')
      |          PsiWhiteSpace(' ')
      |          CaptureSet
      |            PsiElement({)('{')
      |            CaptureRef
      |              CodeReferenceElement: U
      |                PsiElement(identifier)('U')
      |            PsiElement(})('}')
      |          PsiWhiteSpace(' ')
      |          PsiElement(=)('=')
      |          PsiWhiteSpace(' ')
      |          SimpleType: caps.CapSet
      |            CodeReferenceElement: caps.CapSet
      |              CodeReferenceElement: caps
      |                PsiElement(identifier)('caps')
      |              PsiElement(.)('.')
      |              PsiElement(identifier)('CapSet')
      |        PsiWhiteSpace(' ')
      |        PsiElement(})('}')
      |""".stripMargin
  )

  // Unmodified positive fixtures from scala/scala3 tag 3.10.1,
  // tests/pos-custom-args/captures/. Their capture-set semantics are outside SCL-26428.
  def test_filtered_capture_set_bound(): Unit = checkCompilerFixture("except-disjoint-info")

  def test_compiler_except_projection(): Unit = checkCompilerFixture("except-poly")

  def test_compiler_only_projection(): Unit = checkCompilerFixture("restrict-try-poly")

  def test_compiler_nested_transaction_callback(): Unit = checkCompilerFixture("except-transaction-double")

  private def checkCompilerFixture(name: String): Unit = {
    val directory = Path.of(TestUtils.getTestDataPath, "parser", "scala3", "captureSetParameters")
    val source = Files.readString(directory.resolve(s"$name.scala"))
    val expectedTree = Files.readString(directory.resolve(s"$name.tree"))
    checkTree(checkParseErrors(source), expectedTree)
  }

  // Matrices retain diagnostics and recovery checks for operator, comment and prefix variants.
  def test_incomplete_capture_set_parameters_and_bounds(): Unit = {
    val cases = Seq(
      (s"C^ <:${err("Wrong type")} , T", Seq("C", "T")),
      (s"C^ >:${err("Wrong type")} <: {any}, T", Seq("C", "T")),
      (s"C^ <: {any${err("'}' expected")}", Seq("C")),
      (s"C^ <: {any${err("'}' expected")}, D^", Seq("C", "D")),
      (s"C^ <: {any${err("'}' expected")}, T <: Object", Seq("C", "T")),
      (s"C^ <: {any${err("'}' expected")}, F[_]", Seq("C", "F")),
      (s"C^ <: {any${err("'}' expected")}, T: Ordering", Seq("C", "T")),
      (s"C^ <: {any.except[${err("Wrong qualified identifier")}\n]${err("'}' expected")}, D^", Seq("C", "D"))
    )
    for ((parameters, expectedNames) <- cases) {
      checkIncompleteCaptureBounds(parameters, expectedNames)
    }
  }

  private def checkIncompleteCaptureBounds(parameters: String, expectedNames: Seq[String]): Unit = {
    val file = checkParseErrors(s"def f[$parameters](x: Object): Unit = ()\ndef after = ()")
    val functions = childrenOfType(file, FUNCTION_DEFINITION)
    assertEquals(parameters, Seq("f", "after"), functions.map(identifierText))
    assertEquals(parameters, expectedNames, typeParametersOf(functions.head).map(identifierText))
    assertEquals(parameters, Seq("()"), childrenOfType(functions.head, UNIT_EXPR).map(_.getText))
  }

  def test_capture_bound_preserves_variant_parameters(): Unit = {
    for (parameter <- Seq("+T", "-T")) {
      checkParameterAfterCaptureBound(parameter)
    }
    checkParameterAfterCaptureBound("+`*`", expectedName = "`*`")
  }

  def test_capture_bound_preserves_annotated_parameters(): Unit = {
    for (parameter <- Seq("@ann T", "@ann +T", "@ann(1) @other -T")) {
      checkParameterAfterCaptureBound(parameter)
    }
  }

  private def checkParameterAfterCaptureBound(parameterText: String, expectedName: String = "T"): Unit = {
    for (closingBrace <- Seq("}", "")) {
      checkParameterPreservedAfterCaptureBound(parameterText, expectedName, closingBrace)
    }
  }

  private def checkParameterPreservedAfterCaptureBound(parameterText: String, expectedName: String, closingBrace: String): Unit = {
    val error = if (closingBrace.isEmpty) err("'}' expected") else ""
    val source = s"class Box[C^ <: {any$error$closingBrace, $parameterText]\nclass After"
    val file = checkParseErrors(source)
    val parameters = assertClassAndParameterRecovery(file, Seq("C", expectedName))
    assertEquals(source, parameterText, parameters.last.getText)
    assertBoundCaptureReferences(parameters.head, Seq("any"))
  }

  def test_symbolic_reach_captures_after_bound_commas(): Unit = {
    for (reference <- Seq("+ *", "- *", "+ *.only[Capability].rd")) {
      checkSymbolicReachCaptureAfterBoundComma(reference)
    }
  }

  private def checkSymbolicReachCaptureAfterBoundComma(reference: String): Unit = {
    val file = checkParseErrors(s"class Box[C^ <: {any, $reference}, T]\nclass After")
    val parameters = assertClassAndParameterRecovery(file, Seq("C", "T"))
    assertBoundCaptureReferences(parameters.head, Seq("any", reference))
  }

  // Capture bounds are adapted from scala/scala3 3.10.1
  // tests/pos-custom-args/captures/cap-paramlists4.scala. The newline rule is covered by
  // tests/neg/trailingCommas.scala and tests/pos/trailingCommas/trailingCommas.scala.
  def test_same_line_trailing_comma_in_capture_bound_requires_capture(): Unit = {
    for (bound <- Seq(">:", "<:"); afterComma <- Seq("", " /* comment */")) {
      checkCaptureBoundTrailingComma(bound, err("Capture reference expected") + afterComma)
    }
  }

  def test_multiline_trailing_comma_in_capture_bound(): Unit = {
    for (bound <- Seq(">:", "<:"); afterComma <- Seq("\n", " /* comment */\n", " // comment\n")) {
      checkCaptureBoundTrailingComma(bound, afterComma)
    }
  }

  private def checkCaptureBoundTrailingComma(boundOperator: String, afterComma: String): Unit = {
    val file = checkParseErrors(s"class Box[C^ $boundOperator {any,$afterComma}, T]\nclass After")
    val parameters = assertClassAndParameterRecovery(file, Seq("C", "T"))
    assertBoundCaptureReferences(parameters.head, Seq("any"))
  }

  private def assertClassAndParameterRecovery(file: PsiElement, expectedNames: Seq[String]): Seq[PsiElement] = {
    val classes = childrenOfType(file, ClassDefinition)
    assertEquals(file.getText, Seq("Box", "After"), classes.map(identifierText))
    val parameters = typeParametersOf(classes.head)
    assertEquals(file.getText, expectedNames, parameters.map(identifierText))
    parameters
  }

  private def typeParametersOf(declaration: PsiElement): Seq[PsiElement] = {
    val Seq(clause) = childrenOfType(declaration, TYPE_PARAM_CLAUSE)
    childrenOfType(clause, TYPE_PARAM)
  }

  private def assertBoundCaptureReferences(parameter: PsiElement, expectedReferences: Seq[String]): Unit = {
    val Seq(bound) = childrenOfType(parameter, CAPTURE_SET)
    assertEquals(parameter.getText, expectedReferences, childrenOfType(bound, CAPTURE_REF).map(_.getText))
  }
}
