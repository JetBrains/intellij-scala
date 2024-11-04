package org.jetbrains.plugins.scala.tasty.reader

import junit.framework.TestCase
import org.junit.Assert.*

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}
import scala.util.control.NonFatal

// TODO
// restore this prefix (don't simplify, root?), why just "Tree" in dotc parameters
// Symbols.super[TypeTags/*scala.reflect.api.TypeTags*/].WeakTypeTag[T] (scala.reflect.internal.Symbols)
// simplify: Boolean parameter
// final scalaVersionSpecific
// zio.Experimental $throws
// parameter.Modifiers - move using and implict to specific tests? implicit / using with regular
// test quotes in textOfType, given, extension, package, qualifier (plus format)
// enum companion: case(), object
// Target names
// Escape chars
// Compare: single dir
// type MirroredElemTypes = EmptyTuple, type MirroredElemTypes = scala.Tuple$package.EmptyTuple
// Anonymous Context Parameters?
// abstract extension
// super
// annotation: parameter, type, string, array
// Nothing -> Any when for parameter (variance)
// type trees
// different name kinds, FQN
// val a, b; val (a, b)
// transparent inline def quotes in the same file
// exhaustive matches
// getOrElse(throw exception)
// gzip
// rely on signed name instead of Apply template parent calls?
// abstract override (order)
// = derived ?
// modifiers order
// detect anonymous givens more reliably?
// how to merge object / implicit class / enum members, index?
// package objects as package objects?
// default argument constants?
// group enum cases
// group extension methods
// combinedUsingClauses?
// ContextBounds: extension[A : Foo] { def method[A : Bar] }
// ContextBounds: [A](implicit evidence$1: Ordering[Int])
// use Unit method result instead of Int
// use objects instead of traits?
// correspondence between parametric type definitions and type lambdas - which to use?
class TastyReaderTest extends TestCase {

  def testAnnotationMembers(): Unit = doTest("annotation/Members")
  def testAnnotationMultiple(): Unit = doTest("annotation/Multiple")
  def testAnnotationParameters(): Unit = doTest("annotation/Parameters")
  def testAnnotationText(): Unit = doTest("annotation/Text")
  def testMemberBeanProperty(): Unit = doTest("member/BeanProperty")
  def testMemberBounds(): Unit = doTest("member/Bounds")
  def testMemberDef(): Unit = doTest("member/Def")
  def testMemberExtensionMethod(): Unit = doTest("member/ExtensionMethod")
  def testMemberGiven(): Unit = doTest("member/Given")
  def testMemberGivenDeferred(): Unit = doTest("member/GivenDeferred")
  def testMemberIdentifiers(): Unit = doTest("member/Identifiers")
  def testMemberInlineModifier(): Unit = doTest("member/InlineModifier")
  def testMemberModifiers(): Unit = doTest("member/Modifiers")
  def testMemberQualifier(): Unit = doTest("member/Qualifier")
  def testMemberThis(): Unit = doTest("member/This")
  def testMemberType(): Unit = doTest("member/Type")
  def testMemberVal(): Unit = doTest("member/Val")
  def testMemberVar(): Unit = doTest("member/Var")
  def testPackage1Package(): Unit = doTest("package1/package")
  def testPackage1Package2Package(): Unit = doTest("package1/package2/package")
  def testPackage1Package2Nested(): Unit = doTest("package1/package2/Nested")
  def testPackage1Package2NestedImport(): Unit = doTest("package1/package2/NestedImport")
  def testPackage1Package2Prefix(): Unit = doTest("package1/package2/Prefix")
  def testPackage1Package2Scope(): Unit = doTest("package1/package2/Scope")
  def testPackage1Members(): Unit = doTest("package1/Members")
  def testPackage1Paths(): Unit = doTest("package1/Root", simpleTypes = false)
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
  def testParameterNamedContextBounds(): Unit = doTest("parameter/NamedContextBounds")
  def testParameterQualifier(): Unit = doTest("parameter/Qualifier")
  def testParameterRepeated(): Unit = doTest("parameter/Repeated")
  def testParameterTrait(): Unit = doTest("parameter/Trait")
  def testParameterType(): Unit = doTest("parameter/Type")
  def testParameterVariance(): Unit = doTest("parameter/Variance") // TODO TypeMember
  def testTypeDefinitionClass(): Unit = doTest("typeDefinition/Class")
  def testTypeDefinitionCompanions(): Unit = doTest("typeDefinition/Companions")
  def testTypeDefinitionDerivation(): Unit = doTest("typeDefinition/Derivation")
  def testTypeDefinitionEnum(): Unit = doTest("typeDefinition/Enum")
  def testTypeDefinitionIdentifiers(): Unit = doTest("typeDefinition/Identifiers")
  def testTypeDefinitionImplicitClass(): Unit = doTest("typeDefinition/ImplicitClass")
  def testTypeDefinitionMembers(): Unit = doTest("typeDefinition/Members")
  def testTypeDefinitionModifiers(): Unit = doTest("typeDefinition/Modifiers")
  def testTypeDefinitionObject(): Unit = doTest("typeDefinition/Object")
  def testTypeDefinitionPrivate(): Unit = doTest("typeDefinition/PackagePrivate")
  def testTypeDefinitionParents(): Unit = doTest("typeDefinition/Parents")
  def testTypeDefinitionQualifier(): Unit = doTest("typeDefinition/Qualifier")
  def testTypeDefinitionSelfType(): Unit = doTest("typeDefinition/SelfType")
  def testTypeDefinitionTrait(): Unit = doTest("typeDefinition/Trait")
  def testTypesAnd(): Unit = doTest("types/And")
//  def testTypesAnnotated(): Unit = doTest("types/Annotated") // SCL-21207
  def testTypesCompound(): Unit = doTest("types/Compound")
  def testTypesConstant(): Unit = doTest("types/Constant")
  def testTypesFunction(): Unit = doTest("types/Function")
  def testTypesFunctionContext(): Unit = doTest("types/FunctionContext")
  def testTypesFunctionPolymorphic(): Unit = doTest("types/FunctionPolymorphic")
  def testTypesIdent(): Unit = doTest("types/Ident")
  def testTypesInfix(): Unit = doTest("types/Infix")
  def testTypesInlined(): Unit = doTest("types/Inlined")
  def testTypesKindProjector(): Unit = doTest("types/KindProjector")
  def testTypesLambda(): Unit = doTest("types/Lambda")
  def testTypesLiteral(): Unit = doTest("types/Literal")
  def testTypesMatch(): Unit = doTest("types/Match")
  def testTypesOr(): Unit = doTest("types/Or")
  def testTypesParameterized(): Unit = doTest("types/Parameterized")
  def testTypesProjection(): Unit = doTest("types/Projection")
  def testTypesRefinement(): Unit = doTest("types/Refinement")
  def testTypesRefs(): Unit = doTest("types/Refs")
  def testTypesSelect(): Unit = doTest("types/Select")
  def testTypesSingleton(): Unit = doTest("types/Singleton")
  def testTypesThis(): Unit = doTest("types/This")
  def testTypesTuple(): Unit = doTest("types/Tuple")
  def testTypesWildcard(): Unit = doTest("types/Wildcard")
  def testAliases(): Unit = doTest("Aliases")
  def testEmptyPackage(): Unit = doTest("EmptyPackage")
  def testNesting(): Unit = doTest("Nesting")

  private def doTest(path: String, simpleTypes: Boolean = true): Unit = {
    val testDataPath = {
      val path = Path.of("scala/tasty-reader/testdata")
      if (Files.exists(path)) path else Path.of("community", path.toString)
    }
    assert(Files.exists(testDataPath), testDataPath.toAbsolutePath)

    val scalaFile = testDataPath.resolve(path + ".scala")
    assertTrue(s"File $scalaFile does not exist", Files.exists(scalaFile))

    val tastyFile: Path = {
      val scalaFileStr = scalaFile.toString
      val packageFile = Path.of(scalaFileStr.replaceFirst("\\.scala$", "\\$package.tasty"))
      if (Files.exists(packageFile)) packageFile
      else Path.of(scalaFileStr.replaceFirst("\\.scala$", ".tasty"))
    }
    assertTrue(s"File $tastyFile doest not exist", Files.exists(tastyFile))

    val tree = TreeReader.treeFrom(readBytes(tastyFile))

    val (sourceFile, actual) = try {
      val treePrinter = new TreePrinter(infixTypes = true) // TODO disable
      treePrinter.fileAndTextOf(tree)
    } catch {
      case NonFatal(e) =>
        Console.err.println(scalaFile)
        throw e
    }

    assertEquals("Scala file name", scalaFile.toFile.getName, sourceFile)

    val expected = new String(readBytes(scalaFile), StandardCharsets.UTF_8)
      .replaceAll(raw"(?s)/\*\*/.*?/\*(.*?)\*/", "$1")
      .replace("\r", "")

    val adjusted = if (!simpleTypes) actual else actual
      .replace("_root_.", "")
      .replace("java.lang.", "")
      .replace("scala.Predef.", "")
      .replaceAll("scala\\.(?!\\w+\\.(?!type))", "")
      .replaceAll("\\w+\\.this\\.(?!type)", "")
      .replaceAll("\\w+\\.(?=this.type)", "")

    assertEquals(s"Content for $path", expected, adjusted)
  }

  private def readBytes(file: Path): Array[Byte] = Files.readAllBytes(file)
}
