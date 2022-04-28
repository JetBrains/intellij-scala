package org.jetbrains.plugins.scala.tasty.reader

import junit.framework.TestCase
import org.junit.Assert.*

import java.io.File
import java.nio.file.{Files, Path}
import scala.util.control.NonFatal

// test quotes in textOfType, given, extension, package, qualifier (plus format)
// enum companion: case(), object
// Target names
// Escape chars
// Compare: single dir
// type MirroredElemTypes = EmptyTuple, type MirroredElemTypes = scala.Tuple$package.EmptyTuple
// Anonymous Context Parameters?
// infix types
// abstract extension
// hkt as arg, lambda
// match types
// super
// annotation: parameter, type, string, array
// TODO Nothing -> Any when for parameter (variance)
// TODO type trees
// TODO different name kinds, FQN
// TODO symbol names `foo`
// TODO val a, b; val (a, b)
// TODO transparent inline def quotes in the same file
// TODO exhaustive matches
// TODO getOrElse(throw exception)
// TODO gzip
// TODO rely on signed name instead of Apply template parent calls?
// TODO FunctionN, TupleN
// TODO infix types (not just & and |)
// TODO self type
// TODO abstract override (order)
// TODO = derived ?
// TODO modifiers order
// TODO detect anonymous givens more reliably?
// TODO how to merge object / implicit class / enum members, index?
// TODO re-elaborate context bounds?
// TODO package objects as package objects?
// TODO default argument constants?
// TODO group enum cases
// TODO group extension methods
// TODO combinedUsingClauses?
// TODO use Unit method result instead of Int
// TODO use objects instead of traits?
// TODO correspondence between parametric type definitions and type lambdas - which to use?
class TastyReaderTest extends TestCase {

  def testAnnotationMembers(): Unit = doTest("annotation/Members")
  def testAnnotationMultiple(): Unit = doTest("annotation/Multiple")
  def testAnnotationParameters(): Unit = doTest("annotation/Parameters")
  def testAnnotationText(): Unit = doTest("annotation/Text")
  def testMemberBounds(): Unit = doTest("member/Bounds")
  def testMemberDef(): Unit = doTest("member/Def")
  def testMemberExtensionMethod(): Unit = doTest("member/ExtensionMethod")
  def testMemberGiven(): Unit = doTest("member/Given")
  def testMemberIdentifiers(): Unit = doTest("member/Identifiers")
  def testMemberInlineModifier(): Unit = doTest("member/InlineModifier")
  def testMemberModifiers(): Unit = doTest("member/Modifiers")
  def testMemberQualifier(): Unit = doTest("member/Qualifier")
  def testMemberThis(): Unit = doTest("member/This")
  def testMemberType(): Unit = doTest("member/Type")
  def testMemberVal(): Unit = doTest("member/Val")
  def testMemberVar(): Unit = doTest("member/Var")
  def testPackage1Package2Package(): Unit = doTest("package1/package2/package")
  def testPackage1Package2Nested(): Unit = doTest("package1/package2/Nested")
  def testPackage1Package2NestedImport(): Unit = doTest("package1/package2/NestedImport")
  def testPackage1Package2Prefix(): Unit = doTest("package1/package2/Prefix")
  def testPackage1Package2Scope(): Unit = doTest("package1/package2/Scope")
  def testPackage1Members(): Unit = doTest("package1/Members")
  def testPackage1TopLevel(): Unit = doTest("package1/topLevel")
  def testParameterBounds(): Unit = doTest("parameter/Bounds")
  def testParameterByName(): Unit = doTest("parameter/ByName")
  def testParameterCaseClass(): Unit = doTest("parameter/CaseClass")
  def testParameterClass(): Unit = doTest("parameter/Class")
  def testParameterContextBounds(): Unit = doTest("parameter/ContextBounds")
  def testParameterDef(): Unit = doTest("parameter/Def")
  def testParameterDefaultArguments(): Unit = doTest("parameter/DefaultArguments")
  def testParameterEnum(): Unit = doTest("parameter/Enum")
  def testParameterEnumCaseClass(): Unit = doTest("parameter/EnumCaseClass")
  def testParameterExtension(): Unit = doTest("parameter/Extension")
  def testParameterExtensionMethod(): Unit = doTest("parameter/ExtensionMethod")
  def testParameterGiven(): Unit = doTest("parameter/Given")
  def testParameterHKT(): Unit = doTest("parameter/HKT")
  def testParameterHKTBounds(): Unit = doTest("parameter/HKTBounds")
  def testParameterHKTVariance(): Unit = doTest("parameter/HKTVariance")
  def testParameterIdentifiers(): Unit = doTest("parameter/Identifiers")
  def testParameterInlineModifier(): Unit = doTest("parameter/InlineModifier")
  def testParameterModifiers(): Unit = doTest("parameter/Modifiers")
  def testParameterQualifier(): Unit = doTest("parameter/Qualifier")
  def testParameterRepeated(): Unit = doTest("parameter/Repeated")
  def testParameterTrait(): Unit = doTest("parameter/Trait")
  def testParameterType(): Unit = doTest("parameter/Type")
  def testParameterVariance(): Unit = doTest("parameter/Variance") // TODO TypeMember
  def testTypeDefinitionClass(): Unit = doTest("typeDefinition/Class")
  def testTypeDefinitionCompanions(): Unit = doTest("typeDefinition/Companions")
  def testTypeDefinitionEnum(): Unit = doTest("typeDefinition/Enum")
  def testTypeDefinitionIdentifiers(): Unit = doTest("typeDefinition/Identifiers")
  def testTypeDefinitionImplicitClass(): Unit = doTest("typeDefinition/ImplicitClass")
  def testTypeDefinitionMembers(): Unit = doTest("typeDefinition/Members")
  def testTypeDefinitionModifiers(): Unit = doTest("typeDefinition/Modifiers")
  def testTypeDefinitionObject(): Unit = doTest("typeDefinition/Object")
  def testTypeDefinitionParents(): Unit = doTest("typeDefinition/Parents")
  def testTypeDefinitionQualifier(): Unit = doTest("typeDefinition/Qualifier")
  def testTypeDefinitionTrait(): Unit = doTest("typeDefinition/Trait")
  def testTypesAnd(): Unit = doTest("types/And")
  def testTypesAnnotated(): Unit = doTest("types/Annotated")
  def testTypesConstant(): Unit = doTest("types/Constant")
  def testTypesFunction(): Unit = doTest("types/Function")
  def testTypesFunctionContext(): Unit = doTest("types/FunctionContext")
  def testTypesFunctionPolymorphic(): Unit = doTest("types/FunctionPolymorphic")
  def testTypesIdent(): Unit = doTest("types/Ident")
  def testTypesLambda(): Unit = doTest("types/Lambda")
  def testTypesLiteral(): Unit = doTest("types/Literal")
  def testTypesOr(): Unit = doTest("types/Or")
  def testTypesProjection(): Unit = doTest("types/Projection")
  def testTypesRefinement(): Unit = doTest("types/Refinement")
  def testTypesRefs(): Unit = doTest("types/Refs")
  def testTypesSelect(): Unit = doTest("types/Select")
  def testTypesSingleton(): Unit = doTest("types/Singleton")
  def testTypesThis(): Unit = doTest("types/This")
  def testTypesTuple(): Unit = doTest("types/Tuple")
  def testTypesWildcard(): Unit = doTest("types/Wildcard")
  def testEmptyPackage(): Unit = doTest("EmptyPackage")
  def testNesting(): Unit = doTest("Nesting")

  private def doTest(path: String): Unit = {
    val scalaFile = getClass.getResource("/" + path + ".scala").getFile
    assertTrue(scalaFile, exists(scalaFile))

    val tastyFile = {
      val packageFile = scalaFile.replaceFirst("\\.scala", "\\$package.tasty")
      if (exists(packageFile)) packageFile else scalaFile.replaceFirst("\\.scala", ".tasty")
    }
    assertTrue(tastyFile, exists(tastyFile))

    val tree = TreeReader.treeFrom(readBytes(tastyFile))

    val (sourceFile, actual) = try {
      val treePrinter = new TreePrinter(privateMembers = true)
      treePrinter.fileAndTextOf(tree)
    } catch {
      case NonFatal(e) =>
        Console.err.println(scalaFile)
        throw e
    }

    assertEquals(new File(scalaFile).getName, sourceFile)

    val expected = new String(readBytes(scalaFile))
      .replaceAll(raw"(?s)/\*\*/.*?/\*(.*?)\*/", "$1")

    assertEquals(path, expected, actual)
  }

  private def exists(path: String): Boolean = new File(path).exists()

  private def readBytes(file: String): Array[Byte] = Files.readAllBytes(Path.of(file))
}
