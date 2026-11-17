package org.jetbrains.plugins.scala.lang.parser.scala3

import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.ScalaVersion
import org.jetbrains.plugins.scala.base.CaptureCheckingTestUtils
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.parser.ScalaElementType.{CAPTURE_REF, CAPTURE_SET, REFERENCE, TYPE_DEFINITION}
import org.jetbrains.plugins.scala.lang.parser.scala3.CaptureCheckingParserTestUtils._
import org.jetbrains.plugins.scala.lang.psi.api.base.types.{ScFunctionalTypeElement, ScPolyFunctionTypeElement}
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScParameter
import org.jetbrains.plugins.scala.project.ScalaFeatures
import org.junit.Assert.{assertEquals, assertTrue}

/**
 * Tests capture-type and function-type syntax, including capture-filter trees and recovery.
 * Filter snapshots pin child ownership and contextual token kinds; diagnostic matrices
 * preserve the next capture and following declaration for every incomplete-filter variant.
 * Focused capture/function PSI suites cover the filter-related accessor contracts.
 */
class CaptureCheckingParserTest extends SimpleScala3ParserTestBase {
  override def scalaCodeParsingFeatures: ScalaFeatures.SerializableScalaFeatures =
    CaptureCheckingTestUtils.parsingFeatures

  // Capture annotations from scala/scala3 tag 3.10.1:
  // tests/pos-custom-args/captures/{any-only,except-chain}.scala
  def test_only_capture_filter(): Unit = checkTree(
    "x: Object^{any.only[SharedCapability]}",
    """ScalaFile
      |  TypedExpression
      |    ReferenceExpression: x
      |      PsiElement(identifier)('x')
      |    PsiElement(:)(':')
      |    PsiWhiteSpace(' ')
      |    CaptureType: Object^{any.only[SharedCapability]}
      |      SimpleType: Object
      |        CodeReferenceElement: Object
      |          PsiElement(identifier)('Object')
      |      PsiElement(^)('^')
      |      CaptureSet
      |        PsiElement({)('{')
      |        CaptureRef
      |          CodeReferenceElement: any
      |            PsiElement(identifier)('any')
      |          CaptureFilter
      |            PsiElement(.)('.')
      |            PsiElement(only)('only')
      |            PsiElement([)('[')
      |            CodeReferenceElement: SharedCapability
      |              PsiElement(identifier)('SharedCapability')
      |            PsiElement(])(']')
      |        PsiElement(})('}')
      |""".stripMargin
  )

  def test_except_capture_filters(): Unit = checkTree(
    "x: Object^{c.except[C].except[D]}",
    """ScalaFile
      |  TypedExpression
      |    ReferenceExpression: x
      |      PsiElement(identifier)('x')
      |    PsiElement(:)(':')
      |    PsiWhiteSpace(' ')
      |    CaptureType: Object^{c.except[C].except[D]}
      |      SimpleType: Object
      |        CodeReferenceElement: Object
      |          PsiElement(identifier)('Object')
      |      PsiElement(^)('^')
      |      CaptureSet
      |        PsiElement({)('{')
      |        CaptureRef
      |          CodeReferenceElement: c
      |            PsiElement(identifier)('c')
      |          CaptureFilter
      |            PsiElement(.)('.')
      |            PsiElement(except)('except')
      |            PsiElement([)('[')
      |            CodeReferenceElement: C
      |              PsiElement(identifier)('C')
      |            PsiElement(])(']')
      |          CaptureFilter
      |            PsiElement(.)('.')
      |            PsiElement(except)('except')
      |            PsiElement([)('[')
      |            CodeReferenceElement: D
      |              PsiElement(identifier)('D')
      |            PsiElement(])(']')
      |        PsiElement(})('}')
      |""".stripMargin
  )

  def test_combined_capture_filters(): Unit = checkTree(
    "x: Object^{c.only[C].except[D].rd}",
    """ScalaFile
      |  TypedExpression
      |    ReferenceExpression: x
      |      PsiElement(identifier)('x')
      |    PsiElement(:)(':')
      |    PsiWhiteSpace(' ')
      |    CaptureType: Object^{c.only[C].except[D].rd}
      |      SimpleType: Object
      |        CodeReferenceElement: Object
      |          PsiElement(identifier)('Object')
      |      PsiElement(^)('^')
      |      CaptureSet
      |        PsiElement({)('{')
      |        CaptureRef
      |          CodeReferenceElement: c
      |            PsiElement(identifier)('c')
      |          CaptureFilter
      |            PsiElement(.)('.')
      |            PsiElement(only)('only')
      |            PsiElement([)('[')
      |            CodeReferenceElement: C
      |              PsiElement(identifier)('C')
      |            PsiElement(])(']')
      |          CaptureFilter
      |            PsiElement(.)('.')
      |            PsiElement(except)('except')
      |            PsiElement([)('[')
      |            CodeReferenceElement: D
      |              PsiElement(identifier)('D')
      |            PsiElement(])(']')
      |          PsiElement(.)('.')
      |          PsiElement(rd)('rd')
      |        PsiElement(})('}')
      |""".stripMargin
  )

  def test_capture_filter_scala3_examples(): Unit = {
    checkParseErrorMessages(
      """import scala.caps.{any, Classifier, Control, SharedCapability}
        |
        |trait C extends SharedCapability, Classifier
        |trait D extends SharedCapability, Classifier
        |
        |val x: Object^{any.only[SharedCapability]} = Object()
        |def f(c: Object^): Object^{c.except[C].except[D]} = ???
        |def combined(c: Object^): Object^{c.only[C].except[D]} = ???
        |
        |// From scala/scala3 3.10.1 tests/pos-custom-args/captures/except-classified.scala
        |def runOnNewThread[T](body: () ->{any.except[Control]} T): T = body()
        |""".stripMargin
    )
  }

  def test_qualified_references(): Unit = checkTreeAndErrors(
    """type F = () ->{this.c.only[p.C].except[p.D].rd} Unit
      |type G = () ?->{c*.only[p.C]} Unit
      |""".stripMargin,
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
      |    FunctionalType: () ->{this.c.only[p.C].except[p.D].rd} Unit
      |      TypeInParenthesis: ()
      |        PsiElement(()('(')
      |        PsiElement())(')')
      |      PsiWhiteSpace(' ')
      |      PsiElement(->)('->')
      |      CaptureSet
      |        PsiElement({)('{')
      |        CaptureRef
      |          CodeReferenceElement: this.c
      |            ThisReference
      |              PsiElement(this)('this')
      |            PsiElement(.)('.')
      |            PsiElement(identifier)('c')
      |          CaptureFilter
      |            PsiElement(.)('.')
      |            PsiElement(only)('only')
      |            PsiElement([)('[')
      |            CodeReferenceElement: p.C
      |              CodeReferenceElement: p
      |                PsiElement(identifier)('p')
      |              PsiElement(.)('.')
      |              PsiElement(identifier)('C')
      |            PsiElement(])(']')
      |          CaptureFilter
      |            PsiElement(.)('.')
      |            PsiElement(except)('except')
      |            PsiElement([)('[')
      |            CodeReferenceElement: p.D
      |              CodeReferenceElement: p
      |                PsiElement(identifier)('p')
      |              PsiElement(.)('.')
      |              PsiElement(identifier)('D')
      |            PsiElement(])(']')
      |          PsiElement(.)('.')
      |          PsiElement(rd)('rd')
      |        PsiElement(})('}')
      |      PsiWhiteSpace(' ')
      |      SimpleType: Unit
      |        CodeReferenceElement: Unit
      |          PsiElement(identifier)('Unit')
      |  PsiWhiteSpace('\n')
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
      |    FunctionalType: () ?->{c*.only[p.C]} Unit
      |      TypeInParenthesis: ()
      |        PsiElement(()('(')
      |        PsiElement())(')')
      |      PsiWhiteSpace(' ')
      |      PsiElement(?->)('?->')
      |      CaptureSet
      |        PsiElement({)('{')
      |        CaptureRef
      |          CodeReferenceElement: c
      |            PsiElement(identifier)('c')
      |          PsiElement(*)('*')
      |          CaptureFilter
      |            PsiElement(.)('.')
      |            PsiElement(only)('only')
      |            PsiElement([)('[')
      |            CodeReferenceElement: p.C
      |              CodeReferenceElement: p
      |                PsiElement(identifier)('p')
      |              PsiElement(.)('.')
      |              PsiElement(identifier)('C')
      |            PsiElement(])(']')
      |        PsiElement(})('}')
      |      PsiWhiteSpace(' ')
      |      SimpleType: Unit
      |        CodeReferenceElement: Unit
      |          PsiElement(identifier)('Unit')
      |  PsiWhiteSpace('\n')
      |""".stripMargin
  )

  // From scala/scala3 3.10.1 tests/pos-custom-args/captures/except-database.scala.
  def test_this_only(): Unit = checkTreeAndErrors(
    "type F = () ->{this.only[UserTable]} List[User]",
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
      |    FunctionalType: () ->{this.only[UserTable]} List[User]
      |      TypeInParenthesis: ()
      |        PsiElement(()('(')
      |        PsiElement())(')')
      |      PsiWhiteSpace(' ')
      |      PsiElement(->)('->')
      |      CaptureSet
      |        PsiElement({)('{')
      |        CaptureRef
      |          ThisReference
      |            PsiElement(this)('this')
      |          CaptureFilter
      |            PsiElement(.)('.')
      |            PsiElement(only)('only')
      |            PsiElement([)('[')
      |            CodeReferenceElement: UserTable
      |              PsiElement(identifier)('UserTable')
      |            PsiElement(])(']')
      |        PsiElement(})('}')
      |      PsiWhiteSpace(' ')
      |      ParametrizedType: List[User]
      |        SimpleType: List
      |          CodeReferenceElement: List
      |            PsiElement(identifier)('List')
      |        TypeArgumentsList
      |          PsiElement([)('[')
      |          TypeArgument
      |            SimpleType: User
      |              CodeReferenceElement: User
      |                PsiElement(identifier)('User')
      |          PsiElement(])(']')
      |""".stripMargin
  )

  // From scala/scala3 3.10.1 tests/pos-custom-args/captures/except-database.scala.
  def test_this_except(): Unit = checkTreeAndErrors(
    "type F = () ->{this.except[TransactionTable]} List[User]",
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
      |    FunctionalType: () ->{this.except[TransactionTable]} List[User]
      |      TypeInParenthesis: ()
      |        PsiElement(()('(')
      |        PsiElement())(')')
      |      PsiWhiteSpace(' ')
      |      PsiElement(->)('->')
      |      CaptureSet
      |        PsiElement({)('{')
      |        CaptureRef
      |          ThisReference
      |            PsiElement(this)('this')
      |          CaptureFilter
      |            PsiElement(.)('.')
      |            PsiElement(except)('except')
      |            PsiElement([)('[')
      |            CodeReferenceElement: TransactionTable
      |              PsiElement(identifier)('TransactionTable')
      |            PsiElement(])(']')
      |        PsiElement(})('}')
      |      PsiWhiteSpace(' ')
      |      ParametrizedType: List[User]
      |        SimpleType: List
      |          CodeReferenceElement: List
      |            PsiElement(identifier)('List')
      |        TypeArgumentsList
      |          PsiElement([)('[')
      |          TypeArgument
      |            SimpleType: User
      |              CodeReferenceElement: User
      |                PsiElement(identifier)('User')
      |          PsiElement(])(']')
      |""".stripMargin
  )

  // From scala/scala3 3.10.1 tests/pos-custom-args/captures/except-top.scala.
  def test_adjacent_capture(): Unit = checkTreeAndErrors(
    "type T = Object^{c.except[Any], d}",
    """ScalaFile
      |  ScTypeAliasDefinition: T
      |    AnnotationsList
      |      <empty list>
      |    Modifiers
      |      <empty list>
      |    PsiElement(type)('type')
      |    PsiWhiteSpace(' ')
      |    PsiElement(identifier)('T')
      |    PsiWhiteSpace(' ')
      |    PsiElement(=)('=')
      |    PsiWhiteSpace(' ')
      |    CaptureType: Object^{c.except[Any], d}
      |      SimpleType: Object
      |        CodeReferenceElement: Object
      |          PsiElement(identifier)('Object')
      |      PsiElement(^)('^')
      |      CaptureSet
      |        PsiElement({)('{')
      |        CaptureRef
      |          CodeReferenceElement: c
      |            PsiElement(identifier)('c')
      |          CaptureFilter
      |            PsiElement(.)('.')
      |            PsiElement(except)('except')
      |            PsiElement([)('[')
      |            CodeReferenceElement: Any
      |              PsiElement(identifier)('Any')
      |            PsiElement(])(']')
      |        PsiElement(,)(',')
      |        PsiWhiteSpace(' ')
      |        CaptureRef
      |          CodeReferenceElement: d
      |            PsiElement(identifier)('d')
      |        PsiElement(})('}')
      |""".stripMargin
  )

  // From scala/scala3 3.10.1 tests/pos-custom-args/captures/except-captureexception.scala.
  def test_inside_type_argument(): Unit = checkTreeAndErrors(
    "type T = Option[Failure^{f.only[Control]}]",
    """ScalaFile
      |  ScTypeAliasDefinition: T
      |    AnnotationsList
      |      <empty list>
      |    Modifiers
      |      <empty list>
      |    PsiElement(type)('type')
      |    PsiWhiteSpace(' ')
      |    PsiElement(identifier)('T')
      |    PsiWhiteSpace(' ')
      |    PsiElement(=)('=')
      |    PsiWhiteSpace(' ')
      |    ParametrizedType: Option[Failure^{f.only[Control]}]
      |      SimpleType: Option
      |        CodeReferenceElement: Option
      |          PsiElement(identifier)('Option')
      |      TypeArgumentsList
      |        PsiElement([)('[')
      |        TypeArgument
      |          CaptureType: Failure^{f.only[Control]}
      |            SimpleType: Failure
      |              CodeReferenceElement: Failure
      |                PsiElement(identifier)('Failure')
      |            PsiElement(^)('^')
      |            CaptureSet
      |              PsiElement({)('{')
      |              CaptureRef
      |                CodeReferenceElement: f
      |                  PsiElement(identifier)('f')
      |                CaptureFilter
      |                  PsiElement(.)('.')
      |                  PsiElement(only)('only')
      |                  PsiElement([)('[')
      |                  CodeReferenceElement: Control
      |                    PsiElement(identifier)('Control')
      |                  PsiElement(])(']')
      |              PsiElement(})('}')
      |        PsiElement(])(']')
      |""".stripMargin
  )

  // From scala/scala3 3.10.1 tests/pos-custom-args/captures/except-multi-subsumes.scala.
  def test_only_then_multiple_except(): Unit = checkTreeAndErrors(
    "type T = Object^{c.only[SharedCapability].except[Control].except[IOCap]}",
    """ScalaFile
      |  ScTypeAliasDefinition: T
      |    AnnotationsList
      |      <empty list>
      |    Modifiers
      |      <empty list>
      |    PsiElement(type)('type')
      |    PsiWhiteSpace(' ')
      |    PsiElement(identifier)('T')
      |    PsiWhiteSpace(' ')
      |    PsiElement(=)('=')
      |    PsiWhiteSpace(' ')
      |    CaptureType: Object^{c.only[SharedCapability].except[Control].except[IOCap]}
      |      SimpleType: Object
      |        CodeReferenceElement: Object
      |          PsiElement(identifier)('Object')
      |      PsiElement(^)('^')
      |      CaptureSet
      |        PsiElement({)('{')
      |        CaptureRef
      |          CodeReferenceElement: c
      |            PsiElement(identifier)('c')
      |          CaptureFilter
      |            PsiElement(.)('.')
      |            PsiElement(only)('only')
      |            PsiElement([)('[')
      |            CodeReferenceElement: SharedCapability
      |              PsiElement(identifier)('SharedCapability')
      |            PsiElement(])(']')
      |          CaptureFilter
      |            PsiElement(.)('.')
      |            PsiElement(except)('except')
      |            PsiElement([)('[')
      |            CodeReferenceElement: Control
      |              PsiElement(identifier)('Control')
      |            PsiElement(])(']')
      |          CaptureFilter
      |            PsiElement(.)('.')
      |            PsiElement(except)('except')
      |            PsiElement([)('[')
      |            CodeReferenceElement: IOCap
      |              PsiElement(identifier)('IOCap')
      |            PsiElement(])(']')
      |        PsiElement(})('}')
      |""".stripMargin
  )

  // From scala/scala3 3.10.1 tests/pos-custom-args/captures/except-sep/Defs_1.scala.
  def test_except_read_only(): Unit = checkTreeAndErrors(
    "type T = Object^{c.except[Control].rd}",
    """ScalaFile
      |  ScTypeAliasDefinition: T
      |    AnnotationsList
      |      <empty list>
      |    Modifiers
      |      <empty list>
      |    PsiElement(type)('type')
      |    PsiWhiteSpace(' ')
      |    PsiElement(identifier)('T')
      |    PsiWhiteSpace(' ')
      |    PsiElement(=)('=')
      |    PsiWhiteSpace(' ')
      |    CaptureType: Object^{c.except[Control].rd}
      |      SimpleType: Object
      |        CodeReferenceElement: Object
      |          PsiElement(identifier)('Object')
      |      PsiElement(^)('^')
      |      CaptureSet
      |        PsiElement({)('{')
      |        CaptureRef
      |          CodeReferenceElement: c
      |            PsiElement(identifier)('c')
      |          CaptureFilter
      |            PsiElement(.)('.')
      |            PsiElement(except)('except')
      |            PsiElement([)('[')
      |            CodeReferenceElement: Control
      |              PsiElement(identifier)('Control')
      |            PsiElement(])(']')
      |          PsiElement(.)('.')
      |          PsiElement(rd)('rd')
      |        PsiElement(})('}')
      |""".stripMargin
  )

  // From scala/scala3 3.10.1 tests/neg-custom-args/captures/implied-ro.scala; its errors concern semantics.
  def test_only_read_only(): Unit = checkTreeAndErrors(
    "type T = Object^{any.only[Unscoped].rd}",
    """ScalaFile
      |  ScTypeAliasDefinition: T
      |    AnnotationsList
      |      <empty list>
      |    Modifiers
      |      <empty list>
      |    PsiElement(type)('type')
      |    PsiWhiteSpace(' ')
      |    PsiElement(identifier)('T')
      |    PsiWhiteSpace(' ')
      |    PsiElement(=)('=')
      |    PsiWhiteSpace(' ')
      |    CaptureType: Object^{any.only[Unscoped].rd}
      |      SimpleType: Object
      |        CodeReferenceElement: Object
      |          PsiElement(identifier)('Object')
      |      PsiElement(^)('^')
      |      CaptureSet
      |        PsiElement({)('{')
      |        CaptureRef
      |          CodeReferenceElement: any
      |            PsiElement(identifier)('any')
      |          CaptureFilter
      |            PsiElement(.)('.')
      |            PsiElement(only)('only')
      |            PsiElement([)('[')
      |            CodeReferenceElement: Unscoped
      |              PsiElement(identifier)('Unscoped')
      |            PsiElement(])(']')
      |          PsiElement(.)('.')
      |          PsiElement(rd)('rd')
      |        PsiElement(})('}')
      |""".stripMargin
  )

  // From scala/scala3 3.10.1 tests/pos-custom-args/captures/except-effects.scala.
  def test_qualified_context_parameter(): Unit = checkTreeAndErrors(
    "type F = this.Raise ?->{any.except[ErrHandler]} A",
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
      |    FunctionalType: this.Raise ?->{any.except[ErrHandler]} A
      |      SimpleType: this.Raise
      |        CodeReferenceElement: this.Raise
      |          ThisReference
      |            PsiElement(this)('this')
      |          PsiElement(.)('.')
      |          PsiElement(identifier)('Raise')
      |      PsiWhiteSpace(' ')
      |      PsiElement(?->)('?->')
      |      CaptureSet
      |        PsiElement({)('{')
      |        CaptureRef
      |          CodeReferenceElement: any
      |            PsiElement(identifier)('any')
      |          CaptureFilter
      |            PsiElement(.)('.')
      |            PsiElement(except)('except')
      |            PsiElement([)('[')
      |            CodeReferenceElement: ErrHandler
      |              PsiElement(identifier)('ErrHandler')
      |            PsiElement(])(']')
      |        PsiElement(})('}')
      |      PsiWhiteSpace(' ')
      |      SimpleType: A
      |        CodeReferenceElement: A
      |          PsiElement(identifier)('A')
      |""".stripMargin
  )

  // From scala/scala3 3.10.1 tests/pos-custom-args/captures/i19751.scala.
  def test_context_parameter(): Unit = checkTreeAndErrors(
    "type F = Scope ?->{any.only[Shared]} A",
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
      |    FunctionalType: Scope ?->{any.only[Shared]} A
      |      SimpleType: Scope
      |        CodeReferenceElement: Scope
      |          PsiElement(identifier)('Scope')
      |      PsiWhiteSpace(' ')
      |      PsiElement(?->)('?->')
      |      CaptureSet
      |        PsiElement({)('{')
      |        CaptureRef
      |          CodeReferenceElement: any
      |            PsiElement(identifier)('any')
      |          CaptureFilter
      |            PsiElement(.)('.')
      |            PsiElement(only)('only')
      |            PsiElement([)('[')
      |            CodeReferenceElement: Shared
      |              PsiElement(identifier)('Shared')
      |            PsiElement(])(']')
      |        PsiElement(})('}')
      |      PsiWhiteSpace(' ')
      |      SimpleType: A
      |        CodeReferenceElement: A
      |          PsiElement(identifier)('A')
      |""".stripMargin
  )

  def test_missing_open_bracket(): Unit = checkTreeAndErrors(
    source = captureFilterRecoverySource("c.only"),
    expectedErrorMessages = Seq("'[' expected"),
    expectedTree = """ScalaFile
      |  ScTypeAliasDefinition: T
      |    AnnotationsList
      |      <empty list>
      |    Modifiers
      |      <empty list>
      |    PsiElement(type)('type')
      |    PsiWhiteSpace(' ')
      |    PsiElement(identifier)('T')
      |    PsiWhiteSpace(' ')
      |    PsiElement(=)('=')
      |    PsiWhiteSpace(' ')
      |    CaptureType: Object^{c.only, next}
      |      SimpleType: Object
      |        CodeReferenceElement: Object
      |          PsiElement(identifier)('Object')
      |      PsiElement(^)('^')
      |      CaptureSet
      |        PsiElement({)('{')
      |        CaptureRef
      |          CodeReferenceElement: c
      |            PsiElement(identifier)('c')
      |          PsiElement(.)('.')
      |          PsiElement(only)('only')
      |          PsiErrorElement:'[' expected
      |            <empty list>
      |        PsiElement(,)(',')
      |        PsiWhiteSpace(' ')
      |        CaptureRef
      |          CodeReferenceElement: next
      |            PsiElement(identifier)('next')
      |        PsiElement(})('}')
      |  PsiWhiteSpace('\n')
      |  ScTypeAliasDefinition: U
      |    AnnotationsList
      |      <empty list>
      |    Modifiers
      |      <empty list>
      |    PsiElement(type)('type')
      |    PsiWhiteSpace(' ')
      |    PsiElement(identifier)('U')
      |    PsiWhiteSpace(' ')
      |    PsiElement(=)('=')
      |    PsiWhiteSpace(' ')
      |    CaptureType: Object^{after}
      |      SimpleType: Object
      |        CodeReferenceElement: Object
      |          PsiElement(identifier)('Object')
      |      PsiElement(^)('^')
      |      CaptureSet
      |        PsiElement({)('{')
      |        CaptureRef
      |          CodeReferenceElement: after
      |            PsiElement(identifier)('after')
      |        PsiElement(})('}')
      |  PsiWhiteSpace('\n')
      |""".stripMargin
  )

  def test_missing_identifier(): Unit = checkTreeAndErrors(
    source = captureFilterRecoverySource("c.only["),
    expectedErrorMessages = Seq("Wrong qualified identifier"),
    expectedTree = """ScalaFile
      |  ScTypeAliasDefinition: T
      |    AnnotationsList
      |      <empty list>
      |    Modifiers
      |      <empty list>
      |    PsiElement(type)('type')
      |    PsiWhiteSpace(' ')
      |    PsiElement(identifier)('T')
      |    PsiWhiteSpace(' ')
      |    PsiElement(=)('=')
      |    PsiWhiteSpace(' ')
      |    CaptureType: Object^{c.only[, next}
      |      SimpleType: Object
      |        CodeReferenceElement: Object
      |          PsiElement(identifier)('Object')
      |      PsiElement(^)('^')
      |      CaptureSet
      |        PsiElement({)('{')
      |        CaptureRef
      |          CodeReferenceElement: c
      |            PsiElement(identifier)('c')
      |          PsiElement(.)('.')
      |          PsiElement(only)('only')
      |          PsiElement([)('[')
      |          PsiErrorElement:Wrong qualified identifier
      |            <empty list>
      |        PsiElement(,)(',')
      |        PsiWhiteSpace(' ')
      |        CaptureRef
      |          CodeReferenceElement: next
      |            PsiElement(identifier)('next')
      |        PsiElement(})('}')
      |  PsiWhiteSpace('\n')
      |  ScTypeAliasDefinition: U
      |    AnnotationsList
      |      <empty list>
      |    Modifiers
      |      <empty list>
      |    PsiElement(type)('type')
      |    PsiWhiteSpace(' ')
      |    PsiElement(identifier)('U')
      |    PsiWhiteSpace(' ')
      |    PsiElement(=)('=')
      |    PsiWhiteSpace(' ')
      |    CaptureType: Object^{after}
      |      SimpleType: Object
      |        CodeReferenceElement: Object
      |          PsiElement(identifier)('Object')
      |      PsiElement(^)('^')
      |      CaptureSet
      |        PsiElement({)('{')
      |        CaptureRef
      |          CodeReferenceElement: after
      |            PsiElement(identifier)('after')
      |        PsiElement(})('}')
      |  PsiWhiteSpace('\n')
      |""".stripMargin
  )

  def test_empty_argument(): Unit = checkTreeAndErrors(
    source = captureFilterRecoverySource("c.only[]"),
    expectedErrorMessages = Seq("Wrong qualified identifier"),
    expectedTree = """ScalaFile
      |  ScTypeAliasDefinition: T
      |    AnnotationsList
      |      <empty list>
      |    Modifiers
      |      <empty list>
      |    PsiElement(type)('type')
      |    PsiWhiteSpace(' ')
      |    PsiElement(identifier)('T')
      |    PsiWhiteSpace(' ')
      |    PsiElement(=)('=')
      |    PsiWhiteSpace(' ')
      |    CaptureType: Object^{c.only[], next}
      |      SimpleType: Object
      |        CodeReferenceElement: Object
      |          PsiElement(identifier)('Object')
      |      PsiElement(^)('^')
      |      CaptureSet
      |        PsiElement({)('{')
      |        CaptureRef
      |          CodeReferenceElement: c
      |            PsiElement(identifier)('c')
      |          PsiElement(.)('.')
      |          PsiElement(only)('only')
      |          PsiElement([)('[')
      |          PsiErrorElement:Wrong qualified identifier
      |            <empty list>
      |          PsiElement(])(']')
      |        PsiElement(,)(',')
      |        PsiWhiteSpace(' ')
      |        CaptureRef
      |          CodeReferenceElement: next
      |            PsiElement(identifier)('next')
      |        PsiElement(})('}')
      |  PsiWhiteSpace('\n')
      |  ScTypeAliasDefinition: U
      |    AnnotationsList
      |      <empty list>
      |    Modifiers
      |      <empty list>
      |    PsiElement(type)('type')
      |    PsiWhiteSpace(' ')
      |    PsiElement(identifier)('U')
      |    PsiWhiteSpace(' ')
      |    PsiElement(=)('=')
      |    PsiWhiteSpace(' ')
      |    CaptureType: Object^{after}
      |      SimpleType: Object
      |        CodeReferenceElement: Object
      |          PsiElement(identifier)('Object')
      |      PsiElement(^)('^')
      |      CaptureSet
      |        PsiElement({)('{')
      |        CaptureRef
      |          CodeReferenceElement: after
      |            PsiElement(identifier)('after')
      |        PsiElement(})('}')
      |  PsiWhiteSpace('\n')
      |""".stripMargin
  )

  def test_missing_close_bracket(): Unit = checkTreeAndErrors(
    source = captureFilterRecoverySource("c.only[C"),
    expectedErrorMessages = Seq("']' expected"),
    expectedTree = """ScalaFile
      |  ScTypeAliasDefinition: T
      |    AnnotationsList
      |      <empty list>
      |    Modifiers
      |      <empty list>
      |    PsiElement(type)('type')
      |    PsiWhiteSpace(' ')
      |    PsiElement(identifier)('T')
      |    PsiWhiteSpace(' ')
      |    PsiElement(=)('=')
      |    PsiWhiteSpace(' ')
      |    CaptureType: Object^{c.only[C, next}
      |      SimpleType: Object
      |        CodeReferenceElement: Object
      |          PsiElement(identifier)('Object')
      |      PsiElement(^)('^')
      |      CaptureSet
      |        PsiElement({)('{')
      |        CaptureRef
      |          CodeReferenceElement: c
      |            PsiElement(identifier)('c')
      |          CaptureFilter
      |            PsiElement(.)('.')
      |            PsiElement(only)('only')
      |            PsiElement([)('[')
      |            CodeReferenceElement: C
      |              PsiElement(identifier)('C')
      |            PsiErrorElement:']' expected
      |              <empty list>
      |        PsiElement(,)(',')
      |        PsiWhiteSpace(' ')
      |        CaptureRef
      |          CodeReferenceElement: next
      |            PsiElement(identifier)('next')
      |        PsiElement(})('}')
      |  PsiWhiteSpace('\n')
      |  ScTypeAliasDefinition: U
      |    AnnotationsList
      |      <empty list>
      |    Modifiers
      |      <empty list>
      |    PsiElement(type)('type')
      |    PsiWhiteSpace(' ')
      |    PsiElement(identifier)('U')
      |    PsiWhiteSpace(' ')
      |    PsiElement(=)('=')
      |    PsiWhiteSpace(' ')
      |    CaptureType: Object^{after}
      |      SimpleType: Object
      |        CodeReferenceElement: Object
      |          PsiElement(identifier)('Object')
      |      PsiElement(^)('^')
      |      CaptureSet
      |        PsiElement({)('{')
      |        CaptureRef
      |          CodeReferenceElement: after
      |            PsiElement(identifier)('after')
      |        PsiElement(})('}')
      |  PsiWhiteSpace('\n')
      |""".stripMargin
  )

  def test_unfinished_qualified_identifier(): Unit = checkTreeAndErrors(
    source = captureFilterRecoverySource("c.only[p.]"),
    expectedErrorMessages = Seq("Wrong qualified identifier"),
    expectedTree = """ScalaFile
      |  ScTypeAliasDefinition: T
      |    AnnotationsList
      |      <empty list>
      |    Modifiers
      |      <empty list>
      |    PsiElement(type)('type')
      |    PsiWhiteSpace(' ')
      |    PsiElement(identifier)('T')
      |    PsiWhiteSpace(' ')
      |    PsiElement(=)('=')
      |    PsiWhiteSpace(' ')
      |    CaptureType: Object^{c.only[p.], next}
      |      SimpleType: Object
      |        CodeReferenceElement: Object
      |          PsiElement(identifier)('Object')
      |      PsiElement(^)('^')
      |      CaptureSet
      |        PsiElement({)('{')
      |        CaptureRef
      |          CodeReferenceElement: c
      |            PsiElement(identifier)('c')
      |          CaptureFilter
      |            PsiElement(.)('.')
      |            PsiElement(only)('only')
      |            PsiElement([)('[')
      |            CodeReferenceElement: p
      |              PsiElement(identifier)('p')
      |            PsiElement(.)('.')
      |            PsiErrorElement:Wrong qualified identifier
      |              <empty list>
      |            PsiElement(])(']')
      |        PsiElement(,)(',')
      |        PsiWhiteSpace(' ')
      |        CaptureRef
      |          CodeReferenceElement: next
      |            PsiElement(identifier)('next')
      |        PsiElement(})('}')
      |  PsiWhiteSpace('\n')
      |  ScTypeAliasDefinition: U
      |    AnnotationsList
      |      <empty list>
      |    Modifiers
      |      <empty list>
      |    PsiElement(type)('type')
      |    PsiWhiteSpace(' ')
      |    PsiElement(identifier)('U')
      |    PsiWhiteSpace(' ')
      |    PsiElement(=)('=')
      |    PsiWhiteSpace(' ')
      |    CaptureType: Object^{after}
      |      SimpleType: Object
      |        CodeReferenceElement: Object
      |          PsiElement(identifier)('Object')
      |      PsiElement(^)('^')
      |      CaptureSet
      |        PsiElement({)('{')
      |        CaptureRef
      |          CodeReferenceElement: after
      |            PsiElement(identifier)('after')
      |        PsiElement(})('}')
      |  PsiWhiteSpace('\n')
      |""".stripMargin
  )

  def test_unfinished_filter_chain(): Unit = checkTreeAndErrors(
    source = captureFilterRecoverySource("c.only[C].except[]"),
    expectedErrorMessages = Seq("Wrong qualified identifier"),
    expectedTree = """ScalaFile
      |  ScTypeAliasDefinition: T
      |    AnnotationsList
      |      <empty list>
      |    Modifiers
      |      <empty list>
      |    PsiElement(type)('type')
      |    PsiWhiteSpace(' ')
      |    PsiElement(identifier)('T')
      |    PsiWhiteSpace(' ')
      |    PsiElement(=)('=')
      |    PsiWhiteSpace(' ')
      |    CaptureType: Object^{c.only[C].except[], next}
      |      SimpleType: Object
      |        CodeReferenceElement: Object
      |          PsiElement(identifier)('Object')
      |      PsiElement(^)('^')
      |      CaptureSet
      |        PsiElement({)('{')
      |        CaptureRef
      |          CodeReferenceElement: c
      |            PsiElement(identifier)('c')
      |          CaptureFilter
      |            PsiElement(.)('.')
      |            PsiElement(only)('only')
      |            PsiElement([)('[')
      |            CodeReferenceElement: C
      |              PsiElement(identifier)('C')
      |            PsiElement(])(']')
      |          PsiElement(.)('.')
      |          PsiElement(except)('except')
      |          PsiElement([)('[')
      |          PsiErrorElement:Wrong qualified identifier
      |            <empty list>
      |          PsiElement(])(']')
      |        PsiElement(,)(',')
      |        PsiWhiteSpace(' ')
      |        CaptureRef
      |          CodeReferenceElement: next
      |            PsiElement(identifier)('next')
      |        PsiElement(})('}')
      |  PsiWhiteSpace('\n')
      |  ScTypeAliasDefinition: U
      |    AnnotationsList
      |      <empty list>
      |    Modifiers
      |      <empty list>
      |    PsiElement(type)('type')
      |    PsiWhiteSpace(' ')
      |    PsiElement(identifier)('U')
      |    PsiWhiteSpace(' ')
      |    PsiElement(=)('=')
      |    PsiWhiteSpace(' ')
      |    CaptureType: Object^{after}
      |      SimpleType: Object
      |        CodeReferenceElement: Object
      |          PsiElement(identifier)('Object')
      |      PsiElement(^)('^')
      |      CaptureSet
      |        PsiElement({)('{')
      |        CaptureRef
      |          CodeReferenceElement: after
      |            PsiElement(identifier)('after')
      |        PsiElement(})('}')
      |  PsiWhiteSpace('\n')
      |""".stripMargin
  )

  def test_ordinary_identifiers(): Unit = checkTreeAndErrors(
    """def only[T](x: T): T = x
      |def except[T](x: T): T = x
      |val value = only[Int](except[Int](1))
      |val selected = value.only[Int].except[String]
      |type T = Object^{only, except, c.`only`, c.`except`}
      |""".stripMargin,
    """ScalaFile
      |  ScFunctionDefinition: only
      |    AnnotationsList
      |      <empty list>
      |    Modifiers
      |      <empty list>
      |    PsiElement(def)('def')
      |    PsiWhiteSpace(' ')
      |    PsiElement(identifier)('only')
      |    TypeParameterClause
      |      PsiElement([)('[')
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
      |    SimpleType: T
      |      CodeReferenceElement: T
      |        PsiElement(identifier)('T')
      |    PsiWhiteSpace(' ')
      |    PsiElement(=)('=')
      |    PsiWhiteSpace(' ')
      |    ReferenceExpression: x
      |      PsiElement(identifier)('x')
      |  PsiWhiteSpace('\n')
      |  ScFunctionDefinition: except
      |    AnnotationsList
      |      <empty list>
      |    Modifiers
      |      <empty list>
      |    PsiElement(def)('def')
      |    PsiWhiteSpace(' ')
      |    PsiElement(identifier)('except')
      |    TypeParameterClause
      |      PsiElement([)('[')
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
      |    SimpleType: T
      |      CodeReferenceElement: T
      |        PsiElement(identifier)('T')
      |    PsiWhiteSpace(' ')
      |    PsiElement(=)('=')
      |    PsiWhiteSpace(' ')
      |    ReferenceExpression: x
      |      PsiElement(identifier)('x')
      |  PsiWhiteSpace('\n')
      |  ScPatternDefinition: value
      |    AnnotationsList
      |      <empty list>
      |    Modifiers
      |      <empty list>
      |    PsiElement(val)('val')
      |    PsiWhiteSpace(' ')
      |    ListOfPatterns
      |      ReferencePattern: value
      |        PsiElement(identifier)('value')
      |    PsiWhiteSpace(' ')
      |    PsiElement(=)('=')
      |    PsiWhiteSpace(' ')
      |    MethodCall
      |      GenericCall
      |        ReferenceExpression: only
      |          PsiElement(identifier)('only')
      |        TypeArgumentsList
      |          PsiElement([)('[')
      |          TypeArgument
      |            SimpleType: Int
      |              CodeReferenceElement: Int
      |                PsiElement(identifier)('Int')
      |          PsiElement(])(']')
      |      ArgumentList
      |        PsiElement(()('(')
      |        MethodCall
      |          GenericCall
      |            ReferenceExpression: except
      |              PsiElement(identifier)('except')
      |            TypeArgumentsList
      |              PsiElement([)('[')
      |              TypeArgument
      |                SimpleType: Int
      |                  CodeReferenceElement: Int
      |                    PsiElement(identifier)('Int')
      |              PsiElement(])(']')
      |          ArgumentList
      |            PsiElement(()('(')
      |            IntegerLiteral
      |              PsiElement(integer)('1')
      |            PsiElement())(')')
      |        PsiElement())(')')
      |  PsiWhiteSpace('\n')
      |  ScPatternDefinition: selected
      |    AnnotationsList
      |      <empty list>
      |    Modifiers
      |      <empty list>
      |    PsiElement(val)('val')
      |    PsiWhiteSpace(' ')
      |    ListOfPatterns
      |      ReferencePattern: selected
      |        PsiElement(identifier)('selected')
      |    PsiWhiteSpace(' ')
      |    PsiElement(=)('=')
      |    PsiWhiteSpace(' ')
      |    GenericCall
      |      ReferenceExpression: value.only[Int].except
      |        GenericCall
      |          ReferenceExpression: value.only
      |            ReferenceExpression: value
      |              PsiElement(identifier)('value')
      |            PsiElement(.)('.')
      |            PsiElement(identifier)('only')
      |          TypeArgumentsList
      |            PsiElement([)('[')
      |            TypeArgument
      |              SimpleType: Int
      |                CodeReferenceElement: Int
      |                  PsiElement(identifier)('Int')
      |            PsiElement(])(']')
      |        PsiElement(.)('.')
      |        PsiElement(identifier)('except')
      |      TypeArgumentsList
      |        PsiElement([)('[')
      |        TypeArgument
      |          SimpleType: String
      |            CodeReferenceElement: String
      |              PsiElement(identifier)('String')
      |        PsiElement(])(']')
      |  PsiWhiteSpace('\n')
      |  ScTypeAliasDefinition: T
      |    AnnotationsList
      |      <empty list>
      |    Modifiers
      |      <empty list>
      |    PsiElement(type)('type')
      |    PsiWhiteSpace(' ')
      |    PsiElement(identifier)('T')
      |    PsiWhiteSpace(' ')
      |    PsiElement(=)('=')
      |    PsiWhiteSpace(' ')
      |    CaptureType: Object^{only, except, c.`only`, c.`except`}
      |      SimpleType: Object
      |        CodeReferenceElement: Object
      |          PsiElement(identifier)('Object')
      |      PsiElement(^)('^')
      |      CaptureSet
      |        PsiElement({)('{')
      |        CaptureRef
      |          CodeReferenceElement: only
      |            PsiElement(identifier)('only')
      |        PsiElement(,)(',')
      |        PsiWhiteSpace(' ')
      |        CaptureRef
      |          CodeReferenceElement: except
      |            PsiElement(identifier)('except')
      |        PsiElement(,)(',')
      |        PsiWhiteSpace(' ')
      |        CaptureRef
      |          CodeReferenceElement: c.`only`
      |            CodeReferenceElement: c
      |              PsiElement(identifier)('c')
      |            PsiElement(.)('.')
      |            PsiElement(identifier)('`only`')
      |        PsiElement(,)(',')
      |        PsiWhiteSpace(' ')
      |        CaptureRef
      |          CodeReferenceElement: c.`except`
      |            CodeReferenceElement: c
      |              PsiElement(identifier)('c')
      |            PsiElement(.)('.')
      |            PsiElement(identifier)('`except`')
      |        PsiElement(})('}')
      |  PsiWhiteSpace('\n')
      |""".stripMargin
  )

  def test_incomplete_capture_filters(): Unit = {
    for (prefix <- Seq("c.only", "c.except", "c.only[C].except", "c.except[C].except")) {
      val incompleteFilters = Seq(
        (prefix, "'[' expected"),
        (s"$prefix[", "Wrong qualified identifier"),
        (s"$prefix[]", "Wrong qualified identifier"),
        (s"${prefix}[C", "']' expected"),
        (s"$prefix[p.]", "Wrong qualified identifier")
      )
      for ((incomplete, expectedErrorMessage) <- incompleteFilters) {
        checkIncompleteFilterRecovery(incomplete, expectedErrorMessage)
      }
    }
  }

  private def checkIncompleteFilterRecovery(incomplete: String, expectedErrorMessage: String): Unit = {
    val file = checkParseErrorMessages(
      source = captureFilterRecoverySource(incomplete),
      expectedErrorMessages = Seq(expectedErrorMessage)
    )

    val aliases = childrenOfType(file, TYPE_DEFINITION)
    assertEquals(incomplete, Seq("T", "U"), aliases.map(identifierText))
    assertEquals(incomplete, Seq("c", "next"), captureBaseReferenceTexts(aliases.head))
    assertEquals(incomplete, Seq("after"), captureBaseReferenceTexts(aliases.last))
  }

  /** Adds an adjacent capture and following alias to check recovery. */
  private def captureFilterRecoverySource(
    captureText: String
  ): String =
    s"""type T = Object^{$captureText, next}
       |type U = Object^{after}
       |""".stripMargin

  private def captureBaseReferenceTexts(alias: PsiElement): Seq[String] = {
    val Seq(set) = descendantsOfType(alias, CAPTURE_SET)
    childrenOfType(set, CAPTURE_REF).map { capture =>
      val reference = capture.getNode.getFirstChildNode
      assertEquals(capture.getText, REFERENCE, reference.getElementType)
      reference.getText
    }
  }

  def test_polymorphic_pure_function(): Unit = {
    val file = checkParseErrorMessages(
      """object Repro:
        |  def map[F[_]](f: [T] -> T -> F[T]): Unit = ()
        |""".stripMargin
    )

    checkTree(
      file,
      """ScalaFile
        |  ScObject: Repro
        |    AnnotationsList
        |      <empty list>
        |    Modifiers
        |      <empty list>
        |    PsiElement(object)('object')
        |    PsiWhiteSpace(' ')
        |    PsiElement(identifier)('Repro')
        |    ExtendsBlock
        |      ScTemplateBody
        |        PsiElement(:)(':')
        |        PsiWhiteSpace('\n  ')
        |        ScFunctionDefinition: map
        |          AnnotationsList
        |            <empty list>
        |          Modifiers
        |            <empty list>
        |          PsiElement(def)('def')
        |          PsiWhiteSpace(' ')
        |          PsiElement(identifier)('map')
        |          TypeParameterClause
        |            PsiElement([)('[')
        |            TypeParameter: F
        |              PsiElement(identifier)('F')
        |              TypeParameterClause
        |                PsiElement([)('[')
        |                TypeParameter: _
        |                  PsiElement(_)('_')
        |                PsiElement(])(']')
        |            PsiElement(])(']')
        |          Parameters
        |            ParametersClause
        |              PsiElement(()('(')
        |              Parameter: f
        |                AnnotationsList
        |                  <empty list>
        |                Modifiers
        |                  <empty list>
        |                PsiElement(identifier)('f')
        |                PsiElement(:)(':')
        |                PsiWhiteSpace(' ')
        |                ParameterType
        |                  PolymorphicFunctionType: [T] -> T -> F[T]
        |                    TypeParameterClause
        |                      PsiElement([)('[')
        |                      TypeParameter: T
        |                        PsiElement(identifier)('T')
        |                      PsiElement(])(']')
        |                    PsiWhiteSpace(' ')
        |                    PsiElement(->)('->')
        |                    PsiWhiteSpace(' ')
        |                    FunctionalType: T -> F[T]
        |                      SimpleType: T
        |                        CodeReferenceElement: T
        |                          PsiElement(identifier)('T')
        |                      PsiWhiteSpace(' ')
        |                      PsiElement(->)('->')
        |                      PsiWhiteSpace(' ')
        |                      ParametrizedType: F[T]
        |                        SimpleType: F
        |                          CodeReferenceElement: F
        |                            PsiElement(identifier)('F')
        |                        TypeArgumentsList
        |                          PsiElement([)('[')
        |                          TypeArgument
        |                            SimpleType: T
        |                              CodeReferenceElement: T
        |                                PsiElement(identifier)('T')
        |                          PsiElement(])(']')
        |              PsiElement())(')')
        |          PsiElement(:)(':')
        |          PsiWhiteSpace(' ')
        |          SimpleType: Unit
        |            CodeReferenceElement: Unit
        |              PsiElement(identifier)('Unit')
        |          PsiWhiteSpace(' ')
        |          PsiElement(=)('=')
        |          PsiWhiteSpace(' ')
        |          UnitExpression
        |            PsiElement(()('(')
        |            PsiElement())(')')
        |  PsiWhiteSpace('\n')
        |""".stripMargin
    )

    val Seq(parameter) = file.depthFirst().filterByType[ScParameter].toSeq
    val polyType = parameter.typeElement.get.asInstanceOf[ScPolyFunctionTypeElement]
    assertEquals("[T] -> T -> F[T]", polyType.getText)
    assertEquals(Seq("T"), polyType.typeParameters.map(_.name).toSeq)

    val resultType = polyType.resultTypeElement.get.asInstanceOf[ScFunctionalTypeElement]
    assertTrue(resultType.isPure)
    assertEquals("T", resultType.paramTypeElement.getText)
    assertEquals(Some("F[T]"), resultType.returnTypeElement.map(_.getText))
  }

  def test_polymorphic_pure_function_without_capture_checking(): Unit = {
    val file = parseScalaFile(
      "type F = [T] -> T -> T",
      ScalaFeatures.onlyByVersion(ScalaVersion.Latest.Scala_3_8)
    )
    checkTree(
      file,
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
        |    PolymorphicFunctionType: [T]
        |      TypeParameterClause
        |        PsiElement([)('[')
        |        TypeParameter: T
        |          PsiElement(identifier)('T')
        |        PsiElement(])(']')
        |      PsiErrorElement:'=>>' expected
        |        <empty list>
        |  PsiWhiteSpace(' ')
        |  PostfixExpression
        |    InfixExpression
        |      ReferenceExpression: ->
        |        PsiElement(identifier)('->')
        |      PsiWhiteSpace(' ')
        |      ReferenceExpression: T
        |        PsiElement(identifier)('T')
        |      PsiWhiteSpace(' ')
        |      ReferenceExpression: ->
        |        PsiElement(identifier)('->')
        |    PsiWhiteSpace(' ')
        |    ReferenceExpression: T
        |      PsiElement(identifier)('T')
        |""".stripMargin
    )
  }

  def test_polymorphic_pure_context_function_is_rejected(): Unit = checkTree(
    "type F = [T] ?-> T -> T",
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
      |    PolymorphicFunctionType: [T]
      |      TypeParameterClause
      |        PsiElement([)('[')
      |        TypeParameter: T
      |          PsiElement(identifier)('T')
      |        PsiElement(])(']')
      |      PsiErrorElement:'=>>' expected
      |        <empty list>
      |  PsiWhiteSpace(' ')
      |  PsiElement(?->)('?->')
      |  PsiWhiteSpace(' ')
      |  InfixExpression
      |    ReferenceExpression: T
      |      PsiElement(identifier)('T')
      |    PsiWhiteSpace(' ')
      |    ReferenceExpression: ->
      |      PsiElement(identifier)('->')
      |    PsiWhiteSpace(' ')
      |    ReferenceExpression: T
      |      PsiElement(identifier)('T')
      |""".stripMargin
  )

  def test_capture_type(): Unit = checkTree(
    """
      |x: A^
      |x: A^{}
      |x: left ^ right
      |x: left ^ (right)
      |x: left ^ 1
      |x: left ^ "literal"
      |x: (left^) ^ right
      |x: arg^ -> ret
      |x: arg^ ?-> ret
      |""".stripMargin,
    """
      |ScalaFile
      |  PsiWhiteSpace('\n')
      |  TypedExpression
      |    ReferenceExpression: x
      |      PsiElement(identifier)('x')
      |    PsiElement(:)(':')
      |    PsiWhiteSpace(' ')
      |    CaptureType: A^
      |      SimpleType: A
      |        CodeReferenceElement: A
      |          PsiElement(identifier)('A')
      |      PsiElement(^)('^')
      |  PsiWhiteSpace('\n')
      |  TypedExpression
      |    ReferenceExpression: x
      |      PsiElement(identifier)('x')
      |    PsiElement(:)(':')
      |    PsiWhiteSpace(' ')
      |    CaptureType: A^{}
      |      SimpleType: A
      |        CodeReferenceElement: A
      |          PsiElement(identifier)('A')
      |      PsiElement(^)('^')
      |      CaptureSet
      |        PsiElement({)('{')
      |        PsiElement(})('}')
      |  PsiWhiteSpace('\n')
      |  TypedExpression
      |    ReferenceExpression: x
      |      PsiElement(identifier)('x')
      |    PsiElement(:)(':')
      |    PsiWhiteSpace(' ')
      |    InfixType: left ^ right
      |      SimpleType: left
      |        CodeReferenceElement: left
      |          PsiElement(identifier)('left')
      |      PsiWhiteSpace(' ')
      |      CodeReferenceElement: ^
      |        PsiElement(identifier)('^')
      |      PsiWhiteSpace(' ')
      |      SimpleType: right
      |        CodeReferenceElement: right
      |          PsiElement(identifier)('right')
      |  PsiWhiteSpace('\n')
      |  TypedExpression
      |    ReferenceExpression: x
      |      PsiElement(identifier)('x')
      |    PsiElement(:)(':')
      |    PsiWhiteSpace(' ')
      |    InfixType: left ^ (right)
      |      SimpleType: left
      |        CodeReferenceElement: left
      |          PsiElement(identifier)('left')
      |      PsiWhiteSpace(' ')
      |      CodeReferenceElement: ^
      |        PsiElement(identifier)('^')
      |      PsiWhiteSpace(' ')
      |      TypeInParenthesis: (right)
      |        PsiElement(()('(')
      |        SimpleType: right
      |          CodeReferenceElement: right
      |            PsiElement(identifier)('right')
      |        PsiElement())(')')
      |  PsiWhiteSpace('\n')
      |  TypedExpression
      |    ReferenceExpression: x
      |      PsiElement(identifier)('x')
      |    PsiElement(:)(':')
      |    PsiWhiteSpace(' ')
      |    InfixType: left ^ 1
      |      SimpleType: left
      |        CodeReferenceElement: left
      |          PsiElement(identifier)('left')
      |      PsiWhiteSpace(' ')
      |      CodeReferenceElement: ^
      |        PsiElement(identifier)('^')
      |      PsiWhiteSpace(' ')
      |      LiteralType: 1
      |        IntegerLiteral
      |          PsiElement(integer)('1')
      |  PsiWhiteSpace('\n')
      |  TypedExpression
      |    ReferenceExpression: x
      |      PsiElement(identifier)('x')
      |    PsiElement(:)(':')
      |    PsiWhiteSpace(' ')
      |    InfixType: left ^ "literal"
      |      SimpleType: left
      |        CodeReferenceElement: left
      |          PsiElement(identifier)('left')
      |      PsiWhiteSpace(' ')
      |      CodeReferenceElement: ^
      |        PsiElement(identifier)('^')
      |      PsiWhiteSpace(' ')
      |      LiteralType: "literal"
      |        StringLiteral
      |          PsiElement(string content)('"literal"')
      |  PsiWhiteSpace('\n')
      |  TypedExpression
      |    ReferenceExpression: x
      |      PsiElement(identifier)('x')
      |    PsiElement(:)(':')
      |    PsiWhiteSpace(' ')
      |    InfixType: (left^) ^ right
      |      TypeInParenthesis: (left^)
      |        PsiElement(()('(')
      |        CaptureType: left^
      |          SimpleType: left
      |            CodeReferenceElement: left
      |              PsiElement(identifier)('left')
      |          PsiElement(^)('^')
      |        PsiElement())(')')
      |      PsiWhiteSpace(' ')
      |      CodeReferenceElement: ^
      |        PsiElement(identifier)('^')
      |      PsiWhiteSpace(' ')
      |      SimpleType: right
      |        CodeReferenceElement: right
      |          PsiElement(identifier)('right')
      |  PsiWhiteSpace('\n')
      |  TypedExpression
      |    ReferenceExpression: x
      |      PsiElement(identifier)('x')
      |    PsiElement(:)(':')
      |    PsiWhiteSpace(' ')
      |    FunctionalType: arg^ -> ret
      |      CaptureType: arg^
      |        SimpleType: arg
      |          CodeReferenceElement: arg
      |            PsiElement(identifier)('arg')
      |        PsiElement(^)('^')
      |      PsiWhiteSpace(' ')
      |      PsiElement(->)('->')
      |      PsiWhiteSpace(' ')
      |      SimpleType: ret
      |        CodeReferenceElement: ret
      |          PsiElement(identifier)('ret')
      |  PsiWhiteSpace('\n')
      |  TypedExpression
      |    ReferenceExpression: x
      |      PsiElement(identifier)('x')
      |    PsiElement(:)(':')
      |    PsiWhiteSpace(' ')
      |    FunctionalType: arg^ ?-> ret
      |      CaptureType: arg^
      |        SimpleType: arg
      |          CodeReferenceElement: arg
      |            PsiElement(identifier)('arg')
      |        PsiElement(^)('^')
      |      PsiWhiteSpace(' ')
      |      PsiElement(?->)('?->')
      |      PsiWhiteSpace(' ')
      |      SimpleType: ret
      |        CodeReferenceElement: ret
      |          PsiElement(identifier)('ret')
      |  PsiWhiteSpace('\n')
      |""".stripMargin
  )

  def test_pure_function(): Unit = checkTree(
    """
      |// pure function
      |x: Int  -> Int   -> Int
      |x: Int  ?-> Int  ?-> Int
      |x: (Int -> Int)  -> Int
      |x: (Int ?-> Int) ?-> Int
      |// dependent functional type
      |x: (x: Int) ?-> x
      |x: (x: Int) ?=> x
      |""".stripMargin,
    """
      |ScalaFile
      |  PsiWhiteSpace('\n')
      |  PsiComment(comment)('// pure function')
      |  PsiWhiteSpace('\n')
      |  TypedExpression
      |    ReferenceExpression: x
      |      PsiElement(identifier)('x')
      |    PsiElement(:)(':')
      |    PsiWhiteSpace(' ')
      |    FunctionalType: Int  -> Int   -> Int
      |      SimpleType: Int
      |        CodeReferenceElement: Int
      |          PsiElement(identifier)('Int')
      |      PsiWhiteSpace('  ')
      |      PsiElement(->)('->')
      |      PsiWhiteSpace(' ')
      |      FunctionalType: Int   -> Int
      |        SimpleType: Int
      |          CodeReferenceElement: Int
      |            PsiElement(identifier)('Int')
      |        PsiWhiteSpace('   ')
      |        PsiElement(->)('->')
      |        PsiWhiteSpace(' ')
      |        SimpleType: Int
      |          CodeReferenceElement: Int
      |            PsiElement(identifier)('Int')
      |  PsiWhiteSpace('\n')
      |  TypedExpression
      |    ReferenceExpression: x
      |      PsiElement(identifier)('x')
      |    PsiElement(:)(':')
      |    PsiWhiteSpace(' ')
      |    FunctionalType: Int  ?-> Int  ?-> Int
      |      SimpleType: Int
      |        CodeReferenceElement: Int
      |          PsiElement(identifier)('Int')
      |      PsiWhiteSpace('  ')
      |      PsiElement(?->)('?->')
      |      PsiWhiteSpace(' ')
      |      FunctionalType: Int  ?-> Int
      |        SimpleType: Int
      |          CodeReferenceElement: Int
      |            PsiElement(identifier)('Int')
      |        PsiWhiteSpace('  ')
      |        PsiElement(?->)('?->')
      |        PsiWhiteSpace(' ')
      |        SimpleType: Int
      |          CodeReferenceElement: Int
      |            PsiElement(identifier)('Int')
      |  PsiWhiteSpace('\n')
      |  TypedExpression
      |    ReferenceExpression: x
      |      PsiElement(identifier)('x')
      |    PsiElement(:)(':')
      |    PsiWhiteSpace(' ')
      |    FunctionalType: (Int -> Int)  -> Int
      |      TypeInParenthesis: (Int -> Int)
      |        PsiElement(()('(')
      |        FunctionalType: Int -> Int
      |          SimpleType: Int
      |            CodeReferenceElement: Int
      |              PsiElement(identifier)('Int')
      |          PsiWhiteSpace(' ')
      |          PsiElement(->)('->')
      |          PsiWhiteSpace(' ')
      |          SimpleType: Int
      |            CodeReferenceElement: Int
      |              PsiElement(identifier)('Int')
      |        PsiElement())(')')
      |      PsiWhiteSpace('  ')
      |      PsiElement(->)('->')
      |      PsiWhiteSpace(' ')
      |      SimpleType: Int
      |        CodeReferenceElement: Int
      |          PsiElement(identifier)('Int')
      |  PsiWhiteSpace('\n')
      |  TypedExpression
      |    ReferenceExpression: x
      |      PsiElement(identifier)('x')
      |    PsiElement(:)(':')
      |    PsiWhiteSpace(' ')
      |    FunctionalType: (Int ?-> Int) ?-> Int
      |      TypeInParenthesis: (Int ?-> Int)
      |        PsiElement(()('(')
      |        FunctionalType: Int ?-> Int
      |          SimpleType: Int
      |            CodeReferenceElement: Int
      |              PsiElement(identifier)('Int')
      |          PsiWhiteSpace(' ')
      |          PsiElement(?->)('?->')
      |          PsiWhiteSpace(' ')
      |          SimpleType: Int
      |            CodeReferenceElement: Int
      |              PsiElement(identifier)('Int')
      |        PsiElement())(')')
      |      PsiWhiteSpace(' ')
      |      PsiElement(?->)('?->')
      |      PsiWhiteSpace(' ')
      |      SimpleType: Int
      |        CodeReferenceElement: Int
      |          PsiElement(identifier)('Int')
      |  PsiWhiteSpace('\n')
      |  PsiComment(comment)('// dependent functional type')
      |  PsiWhiteSpace('\n')
      |  TypedExpression
      |    ReferenceExpression: x
      |      PsiElement(identifier)('x')
      |    PsiElement(:)(':')
      |    PsiWhiteSpace(' ')
      |    DependentFunctionType: (x: Int) ?-> x
      |      ParametersClause
      |        PsiElement(()('(')
      |        Parameter: x
      |          PsiElement(identifier)('x')
      |          PsiElement(:)(':')
      |          PsiWhiteSpace(' ')
      |          ParameterType
      |            SimpleType: Int
      |              CodeReferenceElement: Int
      |                PsiElement(identifier)('Int')
      |        PsiElement())(')')
      |      PsiWhiteSpace(' ')
      |      PsiElement(?->)('?->')
      |      PsiWhiteSpace(' ')
      |      SimpleType: x
      |        CodeReferenceElement: x
      |          PsiElement(identifier)('x')
      |  PsiWhiteSpace('\n')
      |  TypedExpression
      |    ReferenceExpression: x
      |      PsiElement(identifier)('x')
      |    PsiElement(:)(':')
      |    PsiWhiteSpace(' ')
      |    DependentFunctionType: (x: Int) ?=> x
      |      ParametersClause
      |        PsiElement(()('(')
      |        Parameter: x
      |          PsiElement(identifier)('x')
      |          PsiElement(:)(':')
      |          PsiWhiteSpace(' ')
      |          ParameterType
      |            SimpleType: Int
      |              CodeReferenceElement: Int
      |                PsiElement(identifier)('Int')
      |        PsiElement())(')')
      |      PsiWhiteSpace(' ')
      |      PsiElement(?=>)('?=>')
      |      PsiWhiteSpace(' ')
      |      SimpleType: x
      |        CodeReferenceElement: x
      |          PsiElement(identifier)('x')
      |  PsiWhiteSpace('\n')
      |""".stripMargin
  )

  def test_arrow_in_pattern(): Unit = checkTree(
    """
      |val a -> b = ???
      |val a ?-> b = ???
      |val (x: A -> B) = ???
      |val (x: A ?-> B) = ???
      |""".stripMargin,
    """
      |ScalaFile
      |  PsiWhiteSpace('\n')
      |  ScPatternDefinition: a, b
      |    AnnotationsList
      |      <empty list>
      |    Modifiers
      |      <empty list>
      |    PsiElement(val)('val')
      |    PsiWhiteSpace(' ')
      |    ListOfPatterns
      |      InfixPattern
      |        ReferencePattern: a
      |          PsiElement(identifier)('a')
      |        PsiWhiteSpace(' ')
      |        CodeReferenceElement: ->
      |          PsiElement(identifier)('->')
      |        PsiWhiteSpace(' ')
      |        ReferencePattern: b
      |          PsiElement(identifier)('b')
      |    PsiWhiteSpace(' ')
      |    PsiElement(=)('=')
      |    PsiWhiteSpace(' ')
      |    ReferenceExpression: ???
      |      PsiElement(identifier)('???')
      |  PsiWhiteSpace('\n')
      |  ScPatternDefinition: a, b
      |    AnnotationsList
      |      <empty list>
      |    Modifiers
      |      <empty list>
      |    PsiElement(val)('val')
      |    PsiWhiteSpace(' ')
      |    ListOfPatterns
      |      InfixPattern
      |        ReferencePattern: a
      |          PsiElement(identifier)('a')
      |        PsiWhiteSpace(' ')
      |        CodeReferenceElement: ?->
      |          PsiElement(identifier)('?->')
      |        PsiWhiteSpace(' ')
      |        ReferencePattern: b
      |          PsiElement(identifier)('b')
      |    PsiWhiteSpace(' ')
      |    PsiElement(=)('=')
      |    PsiWhiteSpace(' ')
      |    ReferenceExpression: ???
      |      PsiElement(identifier)('???')
      |  PsiWhiteSpace('\n')
      |  ScPatternDefinition: x
      |    AnnotationsList
      |      <empty list>
      |    Modifiers
      |      <empty list>
      |    PsiElement(val)('val')
      |    PsiWhiteSpace(' ')
      |    ListOfPatterns
      |      PatternInParenthesis
      |        PsiElement(()('(')
      |        Scala3 TypedPattern
      |          ReferencePattern: x
      |            PsiElement(identifier)('x')
      |          PsiElement(:)(':')
      |          PsiWhiteSpace(' ')
      |          TypePattern
      |            InfixType: A -> B
      |              SimpleType: A
      |                CodeReferenceElement: A
      |                  PsiElement(identifier)('A')
      |              PsiWhiteSpace(' ')
      |              CodeReferenceElement: ->
      |                PsiElement(identifier)('->')
      |              PsiWhiteSpace(' ')
      |              SimpleType: B
      |                CodeReferenceElement: B
      |                  PsiElement(identifier)('B')
      |        PsiElement())(')')
      |    PsiWhiteSpace(' ')
      |    PsiElement(=)('=')
      |    PsiWhiteSpace(' ')
      |    ReferenceExpression: ???
      |      PsiElement(identifier)('???')
      |  PsiWhiteSpace('\n')
      |  ScPatternDefinition: x
      |    AnnotationsList
      |      <empty list>
      |    Modifiers
      |      <empty list>
      |    PsiElement(val)('val')
      |    PsiWhiteSpace(' ')
      |    ListOfPatterns
      |      PatternInParenthesis
      |        PsiElement(()('(')
      |        Scala3 TypedPattern
      |          ReferencePattern: x
      |            PsiElement(identifier)('x')
      |          PsiElement(:)(':')
      |          PsiWhiteSpace(' ')
      |          TypePattern
      |            InfixType: A ?-> B
      |              SimpleType: A
      |                CodeReferenceElement: A
      |                  PsiElement(identifier)('A')
      |              PsiWhiteSpace(' ')
      |              CodeReferenceElement: ?->
      |                PsiElement(identifier)('?->')
      |              PsiWhiteSpace(' ')
      |              SimpleType: B
      |                CodeReferenceElement: B
      |                  PsiElement(identifier)('B')
      |        PsiElement())(')')
      |    PsiWhiteSpace(' ')
      |    PsiElement(=)('=')
      |    PsiWhiteSpace(' ')
      |    ReferenceExpression: ???
      |      PsiElement(identifier)('???')
      |  PsiWhiteSpace('\n')
      |""".stripMargin
  )

  def test_capture_set_on_functions(): Unit = checkTree(
    """
      |x: Int ->{} Int
      |x: Int ?->{cap} Int
      |x: Int =>{blub, x} Int
      |x: Int ?=>{} Int
      |""".stripMargin,
    """
      |ScalaFile
      |  PsiWhiteSpace('\n')
      |  TypedExpression
      |    ReferenceExpression: x
      |      PsiElement(identifier)('x')
      |    PsiElement(:)(':')
      |    PsiWhiteSpace(' ')
      |    FunctionalType: Int ->{} Int
      |      SimpleType: Int
      |        CodeReferenceElement: Int
      |          PsiElement(identifier)('Int')
      |      PsiWhiteSpace(' ')
      |      PsiElement(->)('->')
      |      CaptureSet
      |        PsiElement({)('{')
      |        PsiElement(})('}')
      |      PsiWhiteSpace(' ')
      |      SimpleType: Int
      |        CodeReferenceElement: Int
      |          PsiElement(identifier)('Int')
      |  PsiWhiteSpace('\n')
      |  TypedExpression
      |    ReferenceExpression: x
      |      PsiElement(identifier)('x')
      |    PsiElement(:)(':')
      |    PsiWhiteSpace(' ')
      |    FunctionalType: Int ?->{cap} Int
      |      SimpleType: Int
      |        CodeReferenceElement: Int
      |          PsiElement(identifier)('Int')
      |      PsiWhiteSpace(' ')
      |      PsiElement(?->)('?->')
      |      CaptureSet
      |        PsiElement({)('{')
      |        CaptureRef
      |          CodeReferenceElement: cap
      |            PsiElement(identifier)('cap')
      |        PsiElement(})('}')
      |      PsiWhiteSpace(' ')
      |      SimpleType: Int
      |        CodeReferenceElement: Int
      |          PsiElement(identifier)('Int')
      |  PsiWhiteSpace('\n')
      |  TypedExpression
      |    ReferenceExpression: x
      |      PsiElement(identifier)('x')
      |    PsiElement(:)(':')
      |    PsiWhiteSpace(' ')
      |    FunctionalType: Int =>{blub, x} Int
      |      SimpleType: Int
      |        CodeReferenceElement: Int
      |          PsiElement(identifier)('Int')
      |      PsiWhiteSpace(' ')
      |      PsiElement(=>)('=>')
      |      CaptureSet
      |        PsiElement({)('{')
      |        CaptureRef
      |          CodeReferenceElement: blub
      |            PsiElement(identifier)('blub')
      |        PsiElement(,)(',')
      |        PsiWhiteSpace(' ')
      |        CaptureRef
      |          CodeReferenceElement: x
      |            PsiElement(identifier)('x')
      |        PsiElement(})('}')
      |      PsiWhiteSpace(' ')
      |      SimpleType: Int
      |        CodeReferenceElement: Int
      |          PsiElement(identifier)('Int')
      |  PsiWhiteSpace('\n')
      |  TypedExpression
      |    ReferenceExpression: x
      |      PsiElement(identifier)('x')
      |    PsiElement(:)(':')
      |    PsiWhiteSpace(' ')
      |    FunctionalType: Int ?=>{} Int
      |      SimpleType: Int
      |        CodeReferenceElement: Int
      |          PsiElement(identifier)('Int')
      |      PsiWhiteSpace(' ')
      |      PsiElement(?=>)('?=>')
      |      CaptureSet
      |        PsiElement({)('{')
      |        PsiElement(})('}')
      |      PsiWhiteSpace(' ')
      |      SimpleType: Int
      |        CodeReferenceElement: Int
      |          PsiElement(identifier)('Int')
      |  PsiWhiteSpace('\n')
      |""".stripMargin
  )

  def test_capture_set(): Unit = checkTree(
    """
      |x: A^{}
      |x: A^{this}
      |x: A^{super.id}
      |x: A^{id.this.this}
      |x: A^{id.super.id.super[x].id}
      |x: A^{id}
      |x: A^{id.id}
      |x: A^{id, id, id.id}
      |x: A^{id.id.rd}
      |x: A^{id.id.as[B]}
      |x: A^{id.id.as[B].rd}
      |x: A^{id.id*}
      |x: A^{id.id*.rd}
      |x: A^{id.id*.as[B]}
      |x: A^{id.id*.as[B].rd}
      |""".stripMargin,
    """
      |ScalaFile
      |  PsiWhiteSpace('\n')
      |  TypedExpression
      |    ReferenceExpression: x
      |      PsiElement(identifier)('x')
      |    PsiElement(:)(':')
      |    PsiWhiteSpace(' ')
      |    CaptureType: A^{}
      |      SimpleType: A
      |        CodeReferenceElement: A
      |          PsiElement(identifier)('A')
      |      PsiElement(^)('^')
      |      CaptureSet
      |        PsiElement({)('{')
      |        PsiElement(})('}')
      |  PsiWhiteSpace('\n')
      |  TypedExpression
      |    ReferenceExpression: x
      |      PsiElement(identifier)('x')
      |    PsiElement(:)(':')
      |    PsiWhiteSpace(' ')
      |    CaptureType: A^{this}
      |      SimpleType: A
      |        CodeReferenceElement: A
      |          PsiElement(identifier)('A')
      |      PsiElement(^)('^')
      |      CaptureSet
      |        PsiElement({)('{')
      |        CaptureRef
      |          ThisReference
      |            PsiElement(this)('this')
      |        PsiElement(})('}')
      |  PsiWhiteSpace('\n')
      |  TypedExpression
      |    ReferenceExpression: x
      |      PsiElement(identifier)('x')
      |    PsiElement(:)(':')
      |    PsiWhiteSpace(' ')
      |    CaptureType: A^{super.id}
      |      SimpleType: A
      |        CodeReferenceElement: A
      |          PsiElement(identifier)('A')
      |      PsiElement(^)('^')
      |      CaptureSet
      |        PsiElement({)('{')
      |        CaptureRef
      |          CodeReferenceElement: super.id
      |            SuperReference
      |              PsiElement(super)('super')
      |            PsiElement(.)('.')
      |            PsiElement(identifier)('id')
      |        PsiElement(})('}')
      |  PsiWhiteSpace('\n')
      |  TypedExpression
      |    ReferenceExpression: x
      |      PsiElement(identifier)('x')
      |    PsiElement(:)(':')
      |    PsiWhiteSpace(' ')
      |    CaptureType: A^{id.this.this}
      |      SimpleType: A
      |        CodeReferenceElement: A
      |          PsiElement(identifier)('A')
      |      PsiElement(^)('^')
      |      CaptureSet
      |        PsiElement({)('{')
      |        CaptureRef
      |          ThisReference
      |            ThisReference
      |              CodeReferenceElement: id
      |                PsiElement(identifier)('id')
      |              PsiElement(.)('.')
      |              PsiElement(this)('this')
      |            PsiElement(.)('.')
      |            PsiElement(this)('this')
      |        PsiElement(})('}')
      |  PsiWhiteSpace('\n')
      |  TypedExpression
      |    ReferenceExpression: x
      |      PsiElement(identifier)('x')
      |    PsiElement(:)(':')
      |    PsiWhiteSpace(' ')
      |    CaptureType: A^{id.super.id.super[x].id}
      |      SimpleType: A
      |        CodeReferenceElement: A
      |          PsiElement(identifier)('A')
      |      PsiElement(^)('^')
      |      CaptureSet
      |        PsiElement({)('{')
      |        CaptureRef
      |          CodeReferenceElement: id.super.id.super[x].id
      |            SuperReference
      |              CodeReferenceElement: id.super.id
      |                SuperReference
      |                  CodeReferenceElement: id
      |                    PsiElement(identifier)('id')
      |                  PsiElement(.)('.')
      |                  PsiElement(super)('super')
      |                PsiElement(.)('.')
      |                PsiElement(identifier)('id')
      |              PsiElement(.)('.')
      |              PsiElement(super)('super')
      |              PsiElement([)('[')
      |              PsiElement(identifier)('x')
      |              PsiElement(])(']')
      |            PsiElement(.)('.')
      |            PsiElement(identifier)('id')
      |        PsiElement(})('}')
      |  PsiWhiteSpace('\n')
      |  TypedExpression
      |    ReferenceExpression: x
      |      PsiElement(identifier)('x')
      |    PsiElement(:)(':')
      |    PsiWhiteSpace(' ')
      |    CaptureType: A^{id}
      |      SimpleType: A
      |        CodeReferenceElement: A
      |          PsiElement(identifier)('A')
      |      PsiElement(^)('^')
      |      CaptureSet
      |        PsiElement({)('{')
      |        CaptureRef
      |          CodeReferenceElement: id
      |            PsiElement(identifier)('id')
      |        PsiElement(})('}')
      |  PsiWhiteSpace('\n')
      |  TypedExpression
      |    ReferenceExpression: x
      |      PsiElement(identifier)('x')
      |    PsiElement(:)(':')
      |    PsiWhiteSpace(' ')
      |    CaptureType: A^{id.id}
      |      SimpleType: A
      |        CodeReferenceElement: A
      |          PsiElement(identifier)('A')
      |      PsiElement(^)('^')
      |      CaptureSet
      |        PsiElement({)('{')
      |        CaptureRef
      |          CodeReferenceElement: id.id
      |            CodeReferenceElement: id
      |              PsiElement(identifier)('id')
      |            PsiElement(.)('.')
      |            PsiElement(identifier)('id')
      |        PsiElement(})('}')
      |  PsiWhiteSpace('\n')
      |  TypedExpression
      |    ReferenceExpression: x
      |      PsiElement(identifier)('x')
      |    PsiElement(:)(':')
      |    PsiWhiteSpace(' ')
      |    CaptureType: A^{id, id, id.id}
      |      SimpleType: A
      |        CodeReferenceElement: A
      |          PsiElement(identifier)('A')
      |      PsiElement(^)('^')
      |      CaptureSet
      |        PsiElement({)('{')
      |        CaptureRef
      |          CodeReferenceElement: id
      |            PsiElement(identifier)('id')
      |        PsiElement(,)(',')
      |        PsiWhiteSpace(' ')
      |        CaptureRef
      |          CodeReferenceElement: id
      |            PsiElement(identifier)('id')
      |        PsiElement(,)(',')
      |        PsiWhiteSpace(' ')
      |        CaptureRef
      |          CodeReferenceElement: id.id
      |            CodeReferenceElement: id
      |              PsiElement(identifier)('id')
      |            PsiElement(.)('.')
      |            PsiElement(identifier)('id')
      |        PsiElement(})('}')
      |  PsiWhiteSpace('\n')
      |  TypedExpression
      |    ReferenceExpression: x
      |      PsiElement(identifier)('x')
      |    PsiElement(:)(':')
      |    PsiWhiteSpace(' ')
      |    CaptureType: A^{id.id.rd}
      |      SimpleType: A
      |        CodeReferenceElement: A
      |          PsiElement(identifier)('A')
      |      PsiElement(^)('^')
      |      CaptureSet
      |        PsiElement({)('{')
      |        CaptureRef
      |          CodeReferenceElement: id.id
      |            CodeReferenceElement: id
      |              PsiElement(identifier)('id')
      |            PsiElement(.)('.')
      |            PsiElement(identifier)('id')
      |          PsiElement(.)('.')
      |          PsiElement(rd)('rd')
      |        PsiElement(})('}')
      |  PsiWhiteSpace('\n')
      |  TypedExpression
      |    ReferenceExpression: x
      |      PsiElement(identifier)('x')
      |    PsiElement(:)(':')
      |    PsiWhiteSpace(' ')
      |    CaptureType: A^{id.id.as[B]}
      |      SimpleType: A
      |        CodeReferenceElement: A
      |          PsiElement(identifier)('A')
      |      PsiElement(^)('^')
      |      CaptureSet
      |        PsiElement({)('{')
      |        CaptureRef
      |          CodeReferenceElement: id.id
      |            CodeReferenceElement: id
      |              PsiElement(identifier)('id')
      |            PsiElement(.)('.')
      |            PsiElement(identifier)('id')
      |          CaptureFilter
      |            PsiElement(.)('.')
      |            PsiElement(as)('as')
      |            PsiElement([)('[')
      |            CodeReferenceElement: B
      |              PsiElement(identifier)('B')
      |            PsiElement(])(']')
      |        PsiElement(})('}')
      |  PsiWhiteSpace('\n')
      |  TypedExpression
      |    ReferenceExpression: x
      |      PsiElement(identifier)('x')
      |    PsiElement(:)(':')
      |    PsiWhiteSpace(' ')
      |    CaptureType: A^{id.id.as[B].rd}
      |      SimpleType: A
      |        CodeReferenceElement: A
      |          PsiElement(identifier)('A')
      |      PsiElement(^)('^')
      |      CaptureSet
      |        PsiElement({)('{')
      |        CaptureRef
      |          CodeReferenceElement: id.id
      |            CodeReferenceElement: id
      |              PsiElement(identifier)('id')
      |            PsiElement(.)('.')
      |            PsiElement(identifier)('id')
      |          CaptureFilter
      |            PsiElement(.)('.')
      |            PsiElement(as)('as')
      |            PsiElement([)('[')
      |            CodeReferenceElement: B
      |              PsiElement(identifier)('B')
      |            PsiElement(])(']')
      |          PsiElement(.)('.')
      |          PsiElement(rd)('rd')
      |        PsiElement(})('}')
      |  PsiWhiteSpace('\n')
      |  TypedExpression
      |    ReferenceExpression: x
      |      PsiElement(identifier)('x')
      |    PsiElement(:)(':')
      |    PsiWhiteSpace(' ')
      |    CaptureType: A^{id.id*}
      |      SimpleType: A
      |        CodeReferenceElement: A
      |          PsiElement(identifier)('A')
      |      PsiElement(^)('^')
      |      CaptureSet
      |        PsiElement({)('{')
      |        CaptureRef
      |          CodeReferenceElement: id.id
      |            CodeReferenceElement: id
      |              PsiElement(identifier)('id')
      |            PsiElement(.)('.')
      |            PsiElement(identifier)('id')
      |          PsiElement(*)('*')
      |        PsiElement(})('}')
      |  PsiWhiteSpace('\n')
      |  TypedExpression
      |    ReferenceExpression: x
      |      PsiElement(identifier)('x')
      |    PsiElement(:)(':')
      |    PsiWhiteSpace(' ')
      |    CaptureType: A^{id.id*.rd}
      |      SimpleType: A
      |        CodeReferenceElement: A
      |          PsiElement(identifier)('A')
      |      PsiElement(^)('^')
      |      CaptureSet
      |        PsiElement({)('{')
      |        CaptureRef
      |          CodeReferenceElement: id.id
      |            CodeReferenceElement: id
      |              PsiElement(identifier)('id')
      |            PsiElement(.)('.')
      |            PsiElement(identifier)('id')
      |          PsiElement(*)('*')
      |          PsiElement(.)('.')
      |          PsiElement(rd)('rd')
      |        PsiElement(})('}')
      |  PsiWhiteSpace('\n')
      |  TypedExpression
      |    ReferenceExpression: x
      |      PsiElement(identifier)('x')
      |    PsiElement(:)(':')
      |    PsiWhiteSpace(' ')
      |    CaptureType: A^{id.id*.as[B]}
      |      SimpleType: A
      |        CodeReferenceElement: A
      |          PsiElement(identifier)('A')
      |      PsiElement(^)('^')
      |      CaptureSet
      |        PsiElement({)('{')
      |        CaptureRef
      |          CodeReferenceElement: id.id
      |            CodeReferenceElement: id
      |              PsiElement(identifier)('id')
      |            PsiElement(.)('.')
      |            PsiElement(identifier)('id')
      |          PsiElement(*)('*')
      |          CaptureFilter
      |            PsiElement(.)('.')
      |            PsiElement(as)('as')
      |            PsiElement([)('[')
      |            CodeReferenceElement: B
      |              PsiElement(identifier)('B')
      |            PsiElement(])(']')
      |        PsiElement(})('}')
      |  PsiWhiteSpace('\n')
      |  TypedExpression
      |    ReferenceExpression: x
      |      PsiElement(identifier)('x')
      |    PsiElement(:)(':')
      |    PsiWhiteSpace(' ')
      |    CaptureType: A^{id.id*.as[B].rd}
      |      SimpleType: A
      |        CodeReferenceElement: A
      |          PsiElement(identifier)('A')
      |      PsiElement(^)('^')
      |      CaptureSet
      |        PsiElement({)('{')
      |        CaptureRef
      |          CodeReferenceElement: id.id
      |            CodeReferenceElement: id
      |              PsiElement(identifier)('id')
      |            PsiElement(.)('.')
      |            PsiElement(identifier)('id')
      |          PsiElement(*)('*')
      |          CaptureFilter
      |            PsiElement(.)('.')
      |            PsiElement(as)('as')
      |            PsiElement([)('[')
      |            CodeReferenceElement: B
      |              PsiElement(identifier)('B')
      |            PsiElement(])(']')
      |          PsiElement(.)('.')
      |          PsiElement(rd)('rd')
      |        PsiElement(})('}')
      |  PsiWhiteSpace('\n')
      |""".stripMargin
  )

  def test_incomplete_capture_set(): Unit = checkTree(
    """
      |x: A^{.}
      |x: A^{,}
      |x: A^{, id}
      |x: A^{.rd}
      |x: A^{.as[x]}
      |x: A^{id.}
      |x: A^{id.as}
      |x: A^{id.as[}
      |x: A^{id.as[]}
      |x: A^{id.as[x}
      |x: A^{id.as[x].}
      |x: A^{id id}
      |x: A^{super}
      |x: A^{super.}
      |x: A^{super.this}
      |x: A^{super.[x]}
      |""".stripMargin,
    """
      |ScalaFile
      |  PsiWhiteSpace('\n')
      |  TypedExpression
      |    ReferenceExpression: x
      |      PsiElement(identifier)('x')
      |    PsiElement(:)(':')
      |    PsiWhiteSpace(' ')
      |    CaptureType: A^{.}
      |      SimpleType: A
      |        CodeReferenceElement: A
      |          PsiElement(identifier)('A')
      |      PsiElement(^)('^')
      |      CaptureSet
      |        PsiElement({)('{')
      |        CaptureRef
      |          PsiErrorElement:Capture reference expected
      |            <empty list>
      |          PsiElement(.)('.')
      |          PsiErrorElement:'only', 'except', 'as' or 'rd' expected
      |            <empty list>
      |        PsiElement(})('}')
      |  PsiWhiteSpace('\n')
      |  TypedExpression
      |    ReferenceExpression: x
      |      PsiElement(identifier)('x')
      |    PsiElement(:)(':')
      |    PsiWhiteSpace(' ')
      |    CaptureType: A^{,}
      |      SimpleType: A
      |        CodeReferenceElement: A
      |          PsiElement(identifier)('A')
      |      PsiElement(^)('^')
      |      CaptureSet
      |        PsiElement({)('{')
      |        PsiErrorElement:Capture reference expected
      |          <empty list>
      |        PsiElement(,)(',')
      |        PsiErrorElement:Capture reference expected
      |          <empty list>
      |        PsiElement(})('}')
      |  PsiWhiteSpace('\n')
      |  TypedExpression
      |    ReferenceExpression: x
      |      PsiElement(identifier)('x')
      |    PsiElement(:)(':')
      |    PsiWhiteSpace(' ')
      |    CaptureType: A^{, id}
      |      SimpleType: A
      |        CodeReferenceElement: A
      |          PsiElement(identifier)('A')
      |      PsiElement(^)('^')
      |      CaptureSet
      |        PsiElement({)('{')
      |        PsiErrorElement:Capture reference expected
      |          <empty list>
      |        PsiElement(,)(',')
      |        PsiWhiteSpace(' ')
      |        CaptureRef
      |          CodeReferenceElement: id
      |            PsiElement(identifier)('id')
      |        PsiElement(})('}')
      |  PsiWhiteSpace('\n')
      |  TypedExpression
      |    ReferenceExpression: x
      |      PsiElement(identifier)('x')
      |    PsiElement(:)(':')
      |    PsiWhiteSpace(' ')
      |    CaptureType: A^{.rd}
      |      SimpleType: A
      |        CodeReferenceElement: A
      |          PsiElement(identifier)('A')
      |      PsiElement(^)('^')
      |      CaptureSet
      |        PsiElement({)('{')
      |        CaptureRef
      |          PsiErrorElement:Capture reference expected
      |            <empty list>
      |          PsiElement(.)('.')
      |          PsiElement(rd)('rd')
      |        PsiElement(})('}')
      |  PsiWhiteSpace('\n')
      |  TypedExpression
      |    ReferenceExpression: x
      |      PsiElement(identifier)('x')
      |    PsiElement(:)(':')
      |    PsiWhiteSpace(' ')
      |    CaptureType: A^{.as[x]}
      |      SimpleType: A
      |        CodeReferenceElement: A
      |          PsiElement(identifier)('A')
      |      PsiElement(^)('^')
      |      CaptureSet
      |        PsiElement({)('{')
      |        CaptureRef
      |          PsiErrorElement:Capture reference expected
      |            <empty list>
      |          CaptureFilter
      |            PsiElement(.)('.')
      |            PsiElement(as)('as')
      |            PsiElement([)('[')
      |            CodeReferenceElement: x
      |              PsiElement(identifier)('x')
      |            PsiElement(])(']')
      |        PsiElement(})('}')
      |  PsiWhiteSpace('\n')
      |  TypedExpression
      |    ReferenceExpression: x
      |      PsiElement(identifier)('x')
      |    PsiElement(:)(':')
      |    PsiWhiteSpace(' ')
      |    CaptureType: A^{id.}
      |      SimpleType: A
      |        CodeReferenceElement: A
      |          PsiElement(identifier)('A')
      |      PsiElement(^)('^')
      |      CaptureSet
      |        PsiElement({)('{')
      |        CaptureRef
      |          CodeReferenceElement: id.
      |            CodeReferenceElement: id
      |              PsiElement(identifier)('id')
      |            PsiElement(.)('.')
      |            PsiErrorElement:Identifier expected
      |              <empty list>
      |        PsiElement(})('}')
      |  PsiWhiteSpace('\n')
      |  TypedExpression
      |    ReferenceExpression: x
      |      PsiElement(identifier)('x')
      |    PsiElement(:)(':')
      |    PsiWhiteSpace(' ')
      |    CaptureType: A^{id.as}
      |      SimpleType: A
      |        CodeReferenceElement: A
      |          PsiElement(identifier)('A')
      |      PsiElement(^)('^')
      |      CaptureSet
      |        PsiElement({)('{')
      |        CaptureRef
      |          CodeReferenceElement: id
      |            PsiElement(identifier)('id')
      |          PsiElement(.)('.')
      |          PsiElement(as)('as')
      |          PsiErrorElement:'[' expected
      |            <empty list>
      |        PsiElement(})('}')
      |  PsiWhiteSpace('\n')
      |  TypedExpression
      |    ReferenceExpression: x
      |      PsiElement(identifier)('x')
      |    PsiElement(:)(':')
      |    PsiWhiteSpace(' ')
      |    CaptureType: A^{id.as[}
      |      SimpleType: A
      |        CodeReferenceElement: A
      |          PsiElement(identifier)('A')
      |      PsiElement(^)('^')
      |      CaptureSet
      |        PsiElement({)('{')
      |        CaptureRef
      |          CodeReferenceElement: id
      |            PsiElement(identifier)('id')
      |          PsiElement(.)('.')
      |          PsiElement(as)('as')
      |          PsiElement([)('[')
      |          PsiErrorElement:Wrong qualified identifier
      |            <empty list>
      |        PsiElement(})('}')
      |  PsiWhiteSpace('\n')
      |  TypedExpression
      |    ReferenceExpression: x
      |      PsiElement(identifier)('x')
      |    PsiElement(:)(':')
      |    PsiWhiteSpace(' ')
      |    CaptureType: A^{id.as[]}
      |      SimpleType: A
      |        CodeReferenceElement: A
      |          PsiElement(identifier)('A')
      |      PsiElement(^)('^')
      |      CaptureSet
      |        PsiElement({)('{')
      |        CaptureRef
      |          CodeReferenceElement: id
      |            PsiElement(identifier)('id')
      |          PsiElement(.)('.')
      |          PsiElement(as)('as')
      |          PsiElement([)('[')
      |          PsiErrorElement:Wrong qualified identifier
      |            <empty list>
      |          PsiElement(])(']')
      |        PsiElement(})('}')
      |  PsiWhiteSpace('\n')
      |  TypedExpression
      |    ReferenceExpression: x
      |      PsiElement(identifier)('x')
      |    PsiElement(:)(':')
      |    PsiWhiteSpace(' ')
      |    CaptureType: A^{id.as[x}
      |      SimpleType: A
      |        CodeReferenceElement: A
      |          PsiElement(identifier)('A')
      |      PsiElement(^)('^')
      |      CaptureSet
      |        PsiElement({)('{')
      |        CaptureRef
      |          CodeReferenceElement: id
      |            PsiElement(identifier)('id')
      |          CaptureFilter
      |            PsiElement(.)('.')
      |            PsiElement(as)('as')
      |            PsiElement([)('[')
      |            CodeReferenceElement: x
      |              PsiElement(identifier)('x')
      |            PsiErrorElement:']' expected
      |              <empty list>
      |        PsiElement(})('}')
      |  PsiWhiteSpace('\n')
      |  TypedExpression
      |    ReferenceExpression: x
      |      PsiElement(identifier)('x')
      |    PsiElement(:)(':')
      |    PsiWhiteSpace(' ')
      |    CaptureType: A^{id.as[x].}
      |      SimpleType: A
      |        CodeReferenceElement: A
      |          PsiElement(identifier)('A')
      |      PsiElement(^)('^')
      |      CaptureSet
      |        PsiElement({)('{')
      |        CaptureRef
      |          CodeReferenceElement: id
      |            PsiElement(identifier)('id')
      |          CaptureFilter
      |            PsiElement(.)('.')
      |            PsiElement(as)('as')
      |            PsiElement([)('[')
      |            CodeReferenceElement: x
      |              PsiElement(identifier)('x')
      |            PsiElement(])(']')
      |          PsiElement(.)('.')
      |          PsiErrorElement:'rd' expected
      |            <empty list>
      |        PsiElement(})('}')
      |  PsiWhiteSpace('\n')
      |  TypedExpression
      |    ReferenceExpression: x
      |      PsiElement(identifier)('x')
      |    PsiElement(:)(':')
      |    PsiWhiteSpace(' ')
      |    CaptureType: A^{id id}
      |      SimpleType: A
      |        CodeReferenceElement: A
      |          PsiElement(identifier)('A')
      |      PsiElement(^)('^')
      |      CaptureSet
      |        PsiElement({)('{')
      |        CaptureRef
      |          CodeReferenceElement: id
      |            PsiElement(identifier)('id')
      |        PsiErrorElement:',' or '}' expected
      |          <empty list>
      |        PsiWhiteSpace(' ')
      |        PsiElement(identifier)('id')
      |        PsiErrorElement:Capture reference expected
      |          <empty list>
      |        PsiElement(})('}')
      |  PsiWhiteSpace('\n')
      |  TypedExpression
      |    ReferenceExpression: x
      |      PsiElement(identifier)('x')
      |    PsiElement(:)(':')
      |    PsiWhiteSpace(' ')
      |    CaptureType: A^{super}
      |      SimpleType: A
      |        CodeReferenceElement: A
      |          PsiElement(identifier)('A')
      |      PsiElement(^)('^')
      |      CaptureSet
      |        PsiElement({)('{')
      |        CaptureRef
      |          SuperReference
      |            PsiElement(super)('super')
      |            PsiErrorElement:'.' or class qualifier expected
      |              <empty list>
      |        PsiElement(})('}')
      |  PsiWhiteSpace('\n')
      |  TypedExpression
      |    ReferenceExpression: x
      |      PsiElement(identifier)('x')
      |    PsiElement(:)(':')
      |    PsiWhiteSpace(' ')
      |    CaptureType: A^{super.}
      |      SimpleType: A
      |        CodeReferenceElement: A
      |          PsiElement(identifier)('A')
      |      PsiElement(^)('^')
      |      CaptureSet
      |        PsiElement({)('{')
      |        CaptureRef
      |          CodeReferenceElement: super.
      |            SuperReference
      |              PsiElement(super)('super')
      |            PsiElement(.)('.')
      |            PsiErrorElement:Identifier expected
      |              <empty list>
      |        PsiElement(})('}')
      |  PsiWhiteSpace('\n')
      |  TypedExpression
      |    ReferenceExpression: x
      |      PsiElement(identifier)('x')
      |    PsiElement(:)(':')
      |    PsiWhiteSpace(' ')
      |    CaptureType: A^{super.this}
      |      SimpleType: A
      |        CodeReferenceElement: A
      |          PsiElement(identifier)('A')
      |      PsiElement(^)('^')
      |      CaptureSet
      |        PsiElement({)('{')
      |        CaptureRef
      |          CodeReferenceElement: super.
      |            SuperReference
      |              PsiElement(super)('super')
      |            PsiElement(.)('.')
      |            PsiErrorElement:Identifier expected
      |              <empty list>
      |        PsiElement(this)('this')
      |        PsiErrorElement:Capture reference expected
      |          <empty list>
      |        PsiElement(})('}')
      |  PsiWhiteSpace('\n')
      |  TypedExpression
      |    ReferenceExpression: x
      |      PsiElement(identifier)('x')
      |    PsiElement(:)(':')
      |    PsiWhiteSpace(' ')
      |    CaptureType: A^{super.[x]}
      |      SimpleType: A
      |        CodeReferenceElement: A
      |          PsiElement(identifier)('A')
      |      PsiElement(^)('^')
      |      CaptureSet
      |        PsiElement({)('{')
      |        CaptureRef
      |          CodeReferenceElement: super.
      |            SuperReference
      |              PsiElement(super)('super')
      |            PsiElement(.)('.')
      |            PsiErrorElement:Identifier expected
      |              <empty list>
      |        PsiElement([)('[')
      |        CaptureRef
      |          CodeReferenceElement: x
      |            PsiElement(identifier)('x')
      |        PsiErrorElement:',' or '}' expected
      |          <empty list>
      |        PsiElement(])(']')
      |        PsiErrorElement:Capture reference expected
      |          <empty list>
      |        PsiElement(})('}')
      |  PsiWhiteSpace('\n')
      |""".stripMargin
  )

  def testParsingAtEof(): Unit = checkTree(
    """
      |x: A^{
      |""".stripMargin,
    """
      |ScalaFile
      |  PsiWhiteSpace('\n')
      |  TypedExpression
      |    ReferenceExpression: x
      |      PsiElement(identifier)('x')
      |    PsiElement(:)(':')
      |    PsiWhiteSpace(' ')
      |    CaptureType: A^{
      |      SimpleType: A
      |        CodeReferenceElement: A
      |          PsiElement(identifier)('A')
      |      PsiElement(^)('^')
      |      CaptureSet
      |        PsiElement({)('{')
      |        PsiErrorElement:Capture reference expected
      |          <empty list>
      |  PsiWhiteSpace('\n')
      |""".stripMargin
  )
}
