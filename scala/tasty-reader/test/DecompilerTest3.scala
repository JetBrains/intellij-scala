package org.jetbrains.plugins.scala.tasty.reader

import dotty.tools.tasty.TastyFormat.isBooleanAttrTag
import dotty.tools.tasty.TastyReader
import junit.framework.TestCase
import org.junit.Assert.*

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}
import scala.jdk.CollectionConverters.*
import scala.util.control.NonFatal

/**
 * Tests the Scala 3 TASTy reader and printer using checked-in, precompiled `.tasty` files.
 * The tests read those bytes directly and check the resulting source filename, Scala outline,
 * and compiler options against expected values. Some tests also modify fixture bytes to check
 * how the reader handles an unknown attribute.
 *
 * This suite does not compile sources or select a Scala SDK. Each fixture contains the compiler
 * version that produced it in its TASTy header, so tests can cover different Scala 3 releases
 * without being tied to one configured Scala version. Running the same fixtures under another
 * SDK version would not change the input bytes.
 *
 * Most `doTest` cases use the Scala 3.0.0 corpus. Cases for later language features select their
 * compiler group explicitly; library regressions read TASTy copied from the original binary JAR.
 * The layout, provenance, and regeneration procedure are documented in
 * `scala/tasty-reader/testdata/README.md`.
 *
 * Overview of decompiler test types and fixture workflows:<br>
 * https://youtrack.jetbrains.com/issue/SCL-24777/Decompiler-tests-documentation
 *
 * Background and rationale for decompiler unit tests:<br>
 * https://youtrack.jetbrains.com/issue/SCL-21045/Decompiler-unit-tests
 *
 * Scala 3 library integration tests:<br>
 * [[org.jetbrains.plugins.scala.text.scala3]]
 */
class DecompilerTest3 extends TestCase {
  private val Scala300Fixtures = Path.of("compiled/scala-3.0.0")
  private val Scala361Fixtures = Path.of("compiled/scala-3.6.1")
  private val BorerFixtures = Path.of("libraries/borer-derivation_3-1.16.1")
  private val Scala310LibraryFixtures = Path.of("libraries/scala-library-3.10.0")

  def testFixtureCompilerVersions(): Unit = {
    val manifest = Files.readAllLines(testDataPath.resolve("fixture-groups.tsv"), StandardCharsets.UTF_8).asScala
      .filterNot(line => line.isBlank || line.startsWith("#"))
      .map(_.split("\t", -1))

    manifest.foreach(columns => assertEquals("Fixture group columns", 5, columns.length))
    val groups = manifest.map { columns =>
      val groupDirectory = testDataPath.resolve(columns(1))
      assertTrue(s"Missing fixture group: $groupDirectory", Files.isDirectory(groupDirectory))
      groupDirectory -> columns(2)
    }

    val stream = Files.walk(testDataPath)
    val tastyFiles = try stream.iterator().asScala.filter(_.toString.endsWith(".tasty")).toVector
    finally stream.close()

    assertTrue("No TASTy fixtures found", tastyFiles.nonEmpty)
    groups.foreach { case (directory, _) =>
      assertTrue(s"No TASTy fixtures in $directory", tastyFiles.exists(_.startsWith(directory)))
    }
    tastyFiles.foreach { tastyFile =>
      val matchingGroups = groups.filter { case (directory, _) => tastyFile.startsWith(directory) }
      assertEquals(s"Fixture must belong to one group: $tastyFile", 1, matchingGroups.size)
      val expectedVersion = matchingGroups.head._2
      val header = new HeaderReader(new TastyReader(Files.readAllBytes(tastyFile))).readFullHeader()
      assertEquals(s"Compiler version of $tastyFile", expectedVersion, header.toolingVersion)
    }
  }

  def testSourceFilenameMetadataAcrossCompilerVersions(): Unit = {
    // TODO: Automate checking new Scala 3 releases and refreshing this representative fixture matrix.
    // 3.9.0 and 3.10.0 cover the source-file metadata change between those releases.
    val compilerVersions = Seq(
      "3.0.0",
      "3.1.3",
      "3.2.2",
      "3.3.9",
      "3.4.3",
      "3.5.2",
      "3.6.1",
      "3.7.4",
      "3.8.4",
      "3.9.0",
      "3.10.0", "3.10.1"
    )
    val fixtureGroups = compilerVersions.map(version => Path.of(s"compiled/scala-$version"))
    fixtureGroups.foreach { group =>
      val tastyFile = testDataPath.resolve(group).resolve("metadata/Declaration.tasty")
      assertTrue(s"Missing source metadata fixture for $group", Files.isRegularFile(tastyFile))

      val result = new TastyImpl().read(Files.readAllBytes(tastyFile))
      assertTrue(s"Cannot decompile $tastyFile", result.isDefined)
      val (sourceName, sourceText, _) = result.get
      assertEquals(s"Source filename for $group", "SourceMetadata.scala", sourceName)
      assertTrue(s"Declaration outline for $group", sourceText.contains("trait Declaration"))
    }
  }

  def testAnnotationMembers(): Unit = doTest("annotation/Members")
  def testAnnotationMultiple(): Unit = doTest("annotation/Multiple")
  def testAnnotationParameters(): Unit = doTest("annotation/Parameters")
  def testAnnotationText(): Unit = doTest("annotation/Text")
  def testMember(): Unit = doTest("member/package")
  def testMemberBeanProperty(): Unit = doTest("member/BeanProperty")
  def testMemberBounds(): Unit = doTest("member/Bounds")
  def testMemberDef(): Unit = doTest("member/Def")
  def testMemberExtensionMethod(): Unit = doTest("member/ExtensionMethod")
  def testMemberGiven(): Unit = doTest("member/Given")
  def testMemberGivenDeferred(): Unit = doTest("member/GivenDeferred", fixtureGroup = Scala361Fixtures)
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
  def testParameterNamedContextBounds(): Unit = doTest("parameter/NamedContextBounds", fixtureGroup = Scala361Fixtures)
  def testParameterQualifier(): Unit = doTest("parameter/Qualifier")
  def testParameterRepeated(): Unit = doTest("parameter/Repeated")
  def testParameterTrait(): Unit = doTest("parameter/Trait")
  def testParameterType(): Unit = doTest("parameter/Type")
  def testParameterVariance(): Unit = doTest("parameter/Variance") // TypeMember?
  def testTypeDefinition(): Unit = doTest("typeDefinition/package")
  def testTypeDefinitionClass(): Unit = doTest("typeDefinition/Class")
  def testTypeDefinitionCompanions(): Unit = doTest("typeDefinition/Companions")
  def testTypeDefinitionDerivation(): Unit = {
    doTest("typeDefinition/Derivation")
    doTest("typeDefinition/Derivation", expectedFilesContents = Seq(
      "Show.tasty" -> """package typeDefinition
                        |
                        |trait Show[A]
                        |
                        |object Show {
                        |  def derived[A]: typeDefinition.Show[A] = ???
                        |}""".stripMargin,
      "Eq.tasty" -> """package typeDefinition
                      |
                      |trait Eq[A]
                      |
                      |object Eq {
                      |  def derived[A]: typeDefinition.Eq[A] = ???
                      |}""".stripMargin,
    ))
  }
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
  def testTypeDefinitionValueClass(): Unit = doTest("typeDefinition/ValueClass")
  def testTypeDefinitionDerivationApi(): Unit =
    doTest("io/bullet/borer/derivation/DerivationApi", fixtureGroup = BorerFixtures)
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
  def testTypesKindProjector(): Unit = doTest("types/KindProjector", expectedCompilerOptions = CompilerOptions(kindProjector = true))
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
  def testTypesThis(): Unit = {
    doTest("types/This")
    doTest("types/This", expectedFilesContents = Seq(
      "This1.tasty" -> """package types
                         |
                         |object This1 {
                         |  object This2
                         |}""".stripMargin,
    ))
  }
  def testTypesTuple(): Unit = doTest("types/Tuple")
  def testTypesWildcard(): Unit = doTest("types/Wildcard")
  def testAliases(): Unit = doTest("Aliases")
  def testEmptyPackage(): Unit = doTest("EmptyPackage")
  def testEmptyPackage2(): Unit = doTest("EmptyPackage2", expectedFilesContents = Seq(
    "ClassInEmptyPackage.tasty" -> "class ClassInEmptyPackage",
    "ObjectInEmptyPackage.tasty" -> "object ObjectInEmptyPackage",
    "EmptyPackage2$package.tasty" ->
      """type TypeAliasToClassInEmptyPackage = ClassInEmptyPackage
        |
        |type TypeAliasToObjectInEmptyPackage = ObjectInEmptyPackage.type""".stripMargin
  ))
  def testNesting(): Unit = doTest("Nesting")

  def testScala310IndexedSeqOpsSourceFile(): Unit = {
    // Scala 3.10 no longer emits @SourceFile; this TASTy is from scala-library-3.10.0.jar.
    val tastyFile = testDataPath.resolve(Scala310LibraryFixtures).resolve("scala/collection/IndexedSeqOps.tasty")
    val result = new TastyImpl().read(Files.readAllBytes(tastyFile))

    assertTrue("IndexedSeqOps.tasty should be decompiled", result.isDefined)
    val (sourceName, sourceText, _) = result.get
    assertEquals("IndexedSeq.scala", sourceName)
    assertTrue("IndexedSeqOps outline should be decompiled", sourceText.contains("trait IndexedSeqOps"))
  }

  def testUnknownAttributeDoesNotPreventDecompilation(): Unit = {
    val tastyFile = testDataPath.resolve(Scala310LibraryFixtures).resolve("scala/collection/IndexedSeqOps.tasty")
    val bytes = Files.readAllBytes(tastyFile)
    val reader = new TastyReader(bytes)
    new HeaderReader(reader).readFullHeader()
    val nameTableReader = new NameTableReader(reader)
    nameTableReader.read()

    var firstAttributeIndex = Option.empty[Int]
    while (!reader.isAtEnd) {
      val sectionName = nameTableReader.nameAtRef(reader.readNameRef()).asSimpleName.toString
      val sectionEnd = reader.readEnd()
      if (sectionName == "Attributes") {
        firstAttributeIndex = Some(reader.currentAddr.index)
      }
      reader.goto(sectionEnd)
    }

    val index = firstAttributeIndex.getOrElse(throw new AssertionError("No Attributes section"))
    assertTrue("Expected a boolean attribute first", isBooleanAttrTag(bytes(index) & 0xff))
    bytes(index) = 33 // Unknown attribute tag, outside the known boolean and string tag ranges.

    val result = new TastyImpl().read(bytes)
    assertTrue("An unknown attribute should not hide a readable AST", result.isDefined)
    val (sourceName, sourceText, _) = result.get
    assertEquals("Unknown.scala", sourceName)
    assertTrue("IndexedSeqOps outline should be decompiled", sourceText.contains("trait IndexedSeqOps"))
  }

  private def testDataPath: Path = {
    val path = Path.of("scala/tasty-reader/testdata")
    val result = if (Files.exists(path)) path else Path.of("community", path.toString)
    assert(Files.exists(result), result.toAbsolutePath)
    result
  }

  private def findTestScalaSourceFile(testFileRelativePath: String, fixtureGroup: Path): Path = {
    val scalaFile = testDataPath.resolve(fixtureGroup).resolve(testFileRelativePath + ".scala").toAbsolutePath
    assertTrue(s"File $scalaFile does not exist", Files.exists(scalaFile))
    scalaFile
  }

  /**
   * @param expectedFilesContents if this collection is empty, check the corresponding file
   *                              with the standard tasty file name mapping (MyClass.scala -> MyClass.tasty)<br>
   *                              if this collection is NOT empty, check the files from this collection
   *
   */
  private def doTest(
    testFileRelativePath: String,
    expectedFilesContents: Seq[(String, String)] = Nil,
    expectedCompilerOptions: CompilerOptions = CompilerOptions.Default,
    simpleTypes: Boolean = true,
    fixtureGroup: Path = Scala300Fixtures
  ): Unit = {
    val testScalaFile = findTestScalaSourceFile(testFileRelativePath, fixtureGroup)

    val expectedFilesContents2: Seq[(Path, String)] = if (expectedFilesContents.nonEmpty) {
      expectedFilesContents.map { case (tastyFileName, expectedTastyContent) =>
        val tastyFile: Path = testScalaFile.getParent.resolve(tastyFileName)
        tastyFile -> expectedTastyContent
      }
    } else {
      val scalaFilePathStr = testScalaFile.toString
      val sameNameTastyFile = Path.of(scalaFilePathStr.replaceFirst("\\.scala$", ".tasty"))
      val sameNamePackageTastyFile = Path.of(scalaFilePathStr.replaceFirst("\\.scala$", "\\$package.tasty"))

      val tastyFile: Path =
        if (Files.exists(sameNameTastyFile))
          sameNameTastyFile
        else if (Files.exists(sameNamePackageTastyFile))
          sameNamePackageTastyFile
        else
          fail(s"File $sameNameTastyFile or $sameNamePackageTastyFile does not exist").asInstanceOf[Nothing]

      val scalaSourceContent = Files.readString(testScalaFile, StandardCharsets.UTF_8)
      val expectedTastyContent = scalaSourceFileToExpectedTastyContent(scalaSourceContent)
      Seq(tastyFile -> expectedTastyContent)
    }

    expectedFilesContents2.foreach { case (tastyFilePath, expectedTastyContent) =>
      doTest(
        testFileRelativePath,
        testScalaFile,
        tastyFilePath,
        simpleTypes,
        expectedTastyContent,
        expectedCompilerOptions,
      )
    }
  }

  private def doTest(
    testFileRelativePath: String,
    scalaFile: Path,
    tastyFile: Path,
    simpleTypes: Boolean,
    expectedTastyContent: String,
    expectedCompilerOptions: CompilerOptions
  ): Unit = {
    val (tree, sourceFile) = TreeReader.treeAndSourceFileFrom(Files.readAllBytes(tastyFile))

    val (actualSourceFile, actualTastyContent, actualCompilerOptions) = try {
      val treePrinter = new TreePrinter(infixTypes = true) // Disable?
      treePrinter.fileAndTextOf(tree, sourceFile)
    } catch {
      case NonFatal(e) =>
        Console.err.println(scalaFile)
        throw e
    }

    val expectedSourceFile = scalaFile.getFileName.toString
    assertEquals("Scala file name", expectedSourceFile, actualSourceFile)

    val actualTastyContentNormalised = if (simpleTypes)
      normaliseTastyContent(actualTastyContent)
    else
      actualTastyContent

    assertEquals(s"Content for $testFileRelativePath (${tastyFile.getFileName})", expectedTastyContent, actualTastyContentNormalised)

    assertEquals(expectedCompilerOptions, actualCompilerOptions)
  }

  private def scalaSourceFileToExpectedTastyContent(scalaFileContent: String): String =
    scalaFileContent
      .replaceAll(raw"(?s)/\*\*/.*?/\*(.*?)\*/", "$1")
      .replace("\r", "")

  private def normaliseTastyContent(tastyContent: String): String =
    tastyContent
      .replace("_root_.", "")
      .replaceAll("java.lang.(?!Boolean|Long|Float|Double|String)", "")
      .replace("scala.Predef.", "")
      .replaceAll("scala\\.(?!\\w+\\.(?!type))", "")
      .replaceAll("\\w+\\.this\\.(?!type)", "")
      .replaceAll("\\w+\\.(?=this.type)", "")
}
