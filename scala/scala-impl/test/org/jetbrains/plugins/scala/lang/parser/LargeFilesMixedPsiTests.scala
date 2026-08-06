package org.jetbrains.plugins.scala.lang.parser

import com.intellij.openapi.fileTypes.PlainTextLanguage
import com.intellij.psi.impl.compiled.ClsFileImpl
import com.intellij.psi.impl.source.PsiFileImpl
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.stubs.{PsiClassHolderFileStub, StubElement}
import com.intellij.psi.{JavaPsiFacade, PsiFile, PsiFileFactory, PsiManager}
import com.intellij.testFramework.PsiTestUtil
import org.jetbrains.plugins.scala.ScalaLanguage
import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestCase
import org.jetbrains.plugins.scala.extensions.{ArrayExt, IterableOnceExt, PsiClassExt, PsiNamedElementExt}
import org.jetbrains.plugins.scala.lang.parser.LargeFilesMixedPsiTests.generateLargeScalaFileWithLargeCommentText
import org.jetbrains.plugins.scala.lang.psi.compiled.ScClsFileViewProvider.ScClsFileImpl
import org.jetbrains.plugins.scala.util.TestUtils
import org.junit.Assert.{assertEquals, fail}

import java.nio.file.{FileSystems, Files, Path}
import scala.jdk.CollectionConverters.{IteratorHasAsScala, ListHasAsScala}
import scala.util.{Random, Using}

/**
 * The test tests logic that is spread among multimple places:
 *  - [[org.jetbrains.plugins.scala.lang.psi.compiled.ScClsFileViewProvider]]
 *  - [[org.jetbrains.plugins.scala.lang.psi.impl.ScalaFileImpl]]
 *
 * @see [[org.jetbrains.plugins.scala.lang.parser.LargeFilesAnnotatorTest.testShowEditorWarningInLargeScalaFiles]]
 * @see [[com.intellij.psi.SingleRootFileViewProvider.isTooLargeForIntelligence]]
 */
class LargeFilesMixedPsiTests extends ScalaLightCodeInsightFixtureTestCase {

  def testLargeScalaFiles_GeneratedFiles(): Unit = {
    val scalaFileText = generateLargeScalaFileWithLargeCommentText
    val psiFactory = PsiFileFactory.getInstance(getProject)
    val psiFile = psiFactory.createFileFromText("Dummy.scala", ScalaLanguage.INSTANCE, scalaFileText)

    assertEquals(scalaFileText, psiFile.getText)
    assertEquals("File should be treated as a plain text file (as being a too large file)", PlainTextLanguage.INSTANCE, psiFile.getLanguage)
  }

  private def jarFileWithBigClassesPath = TestUtils.getTestDataDir.resolve("psiFactory/generated-jar-with-big-class-files_2.13-0.1.0.jar")

  private def addSingleJarFileAsLibrary(jarPath: Path): Unit = {
    val libParentPath = jarPath.getParent
    val libFileName = jarPath.getFileName.toString
    PsiTestUtil.addLibrary(getModule, "generated-jar-with-big-class-files", libParentPath.toString, libFileName)
  }

  private def extractFilesWithContents(jarPath: Path, dirWithSourcesRelativePath: Path): Seq[(String, String)] = {
    Using.resource(FileSystems.newFileSystem(jarPath, null: ClassLoader)) { fileSystem =>
      val pathParts = dirWithSourcesRelativePath.iterator().asScala.map(_.toString).toSeq
      val first = pathParts.head
      val rest = pathParts.tail
      val sourcesRoot = fileSystem.getPath(first, rest: _*)
      val sourceFiles = Using.resource(Files.list(sourcesRoot))(_.filter(Files.isRegularFile(_)).iterator().asScala.toSeq)
      sourceFiles.map { filePath =>
        val content = new String(Files.readAllBytes(filePath), "UTF-8")
        filePath.getFileName.toString -> content
      }.sortBy(_._1)
    }
  }

  def testLargeScalaAndJavaFiles(): Unit = {
    val jarPath = jarFileWithBigClassesPath
    val fileNameToContent: Seq[(String, String)] =
      extractFilesWithContents(jarPath, Path.of("META-INF/generatorProject/src/main/scala/com_example_veryLong_packageName_to_make_the_decompiled_version_larger_faster"))

    //
    // Test that all expected files were extracted from the archive
    //
    val fileNames = fileNameToContent.map(_._1).mkString("\n")
    assertEquals(
      """Java_10KMethods.java
        |Java_1KBigMethods10.java
        |Java_20KMethods.java
        |Java_40KMethods.java
        |Scala_10KMethods.scala
        |Scala_1KBigMethods10.scala
        |Scala_20KMethods.scala
        |Scala_40KMethods.scala
        |""".stripMargin.trim,
      fileNames
    )

    // Add all extracted files to the project sources
    fileNameToContent.foreach { case (fileName, fileContent) =>
      myFixture.addFileToProject(fileName, fileContent)
    }

    //
    // Test that large source files (Scala & Java) are treated as plain text files
    //
    val sourcesVFiles = myFixture.getTempDirFixture.getFile(".").getChildren
    val sourcesPsiFiles: Array[PsiFile] = sourcesVFiles.map { vFile =>
      PsiManager.getInstance(getProject).findFile(vFile)
    }.sortBy(_.getName)

    val fileNamesWithFileClasses: String =
      sourcesPsiFiles.map(f => s"${f.name} -> ${f.getClass.getName}").mkString("\n")
    assertEquals(
      """Java_10KMethods.java -> com.intellij.psi.impl.source.PsiJavaFileImpl
        |Java_1KBigMethods10.java -> com.intellij.psi.impl.source.PsiPlainTextFileImpl
        |Java_20KMethods.java -> com.intellij.psi.impl.source.PsiPlainTextFileImpl
        |Java_40KMethods.java -> com.intellij.psi.impl.source.PsiPlainTextFileImpl
        |Scala_10KMethods.scala -> org.jetbrains.plugins.scala.lang.psi.impl.ScalaFileImpl
        |Scala_1KBigMethods10.scala -> com.intellij.psi.impl.source.PsiPlainTextFileImpl
        |Scala_20KMethods.scala -> com.intellij.psi.impl.source.PsiPlainTextFileImpl
        |Scala_40KMethods.scala -> com.intellij.psi.impl.source.PsiPlainTextFileImpl
        |""".stripMargin.trim,
      fileNamesWithFileClasses
    )

    //
    // Test that large source files (Scala & Java) don't have stubs
    //
    val filesWithStubs = sourcesPsiFiles
      .filterByType[PsiFileImpl]
      .map(file => file -> file.getStub)

    val fileNamesWithStubClasses: String =
      filesWithStubs
        .map { case (file, stub) =>
          s"${file.name} -> ${Option(stub).map(_.getClass.getName).orNull}"
        }
        .mkString("\n")
    assertEquals(
      "Only non-big files should have stubs, big files should have null stubs",
      """Java_10KMethods.java -> com.intellij.psi.impl.java.stubs.impl.PsiJavaFileStubImpl
        |Java_1KBigMethods10.java -> null
        |Java_20KMethods.java -> null
        |Java_40KMethods.java -> null
        |Scala_10KMethods.scala -> org.jetbrains.plugins.scala.lang.psi.stubs.elements.ScStubFileElementType$ScFileStubImpl
        |Scala_1KBigMethods10.scala -> null
        |Scala_20KMethods.scala -> null
        |Scala_40KMethods.scala -> null
        |""".stripMargin.trim,
      fileNamesWithStubClasses
    )
  }

  def testLargeDecompiledScalaAndJavaFiles(): Unit = {
    val jarPath = jarFileWithBigClassesPath
    addSingleJarFileAsLibrary(jarPath)

    val pack = JavaPsiFacade.getInstance(getProject).findPackage("com_example_veryLong_packageName_to_make_the_decompiled_version_larger_fasterFromJar")

    val classes = pack.getClasses(GlobalSearchScope.allScope(getProject)).toSeq.sortBy(_.qualifiedName)
    assertEquals(
      "All classes from the jar files should be indexed (big and not big)",
      """com_example_veryLong_packageName_to_make_the_decompiled_version_larger_fasterFromJar.Java_10KMethodsFromJar
        |com_example_veryLong_packageName_to_make_the_decompiled_version_larger_fasterFromJar.Java_1KBigMethods10FromJar
        |com_example_veryLong_packageName_to_make_the_decompiled_version_larger_fasterFromJar.Java_20KMethodsFromJar
        |com_example_veryLong_packageName_to_make_the_decompiled_version_larger_fasterFromJar.Java_40KMethodsFromJar
        |com_example_veryLong_packageName_to_make_the_decompiled_version_larger_fasterFromJar.Scala_10KMethodsFromJar
        |com_example_veryLong_packageName_to_make_the_decompiled_version_larger_fasterFromJar.Scala_1KBigMethods10FromJar
        |com_example_veryLong_packageName_to_make_the_decompiled_version_larger_fasterFromJar.Scala_20KMethodsFromJar
        |com_example_veryLong_packageName_to_make_the_decompiled_version_larger_fasterFromJar.Scala_40KMethodsFromJar
        |""".stripMargin.trim,
      classes.map(_.qualifiedName).mkString("\n"),
    )

    val files: Seq[PsiFile] = classes.map(_.getContainingFile.getViewProvider).map { vp =>
      val files = vp.getAllFiles.asScala.toSeq
      files match {
        case Seq(file) => file
        case files =>
          fail(s"Expected a single file from $vp, but got: $files").asInstanceOf[Nothing]
      }
    }

    val filesClassNames = files.map(_.getClass.getName)
    assertEquals(
      """com.intellij.psi.impl.compiled.ClsFileImpl
        |com.intellij.psi.impl.compiled.ClsFileImpl
        |com.intellij.psi.impl.compiled.ClsFileImpl
        |com.intellij.psi.impl.compiled.ClsFileImpl
        |org.jetbrains.plugins.scala.lang.psi.compiled.ScClsFileViewProvider$ScClsFileImpl
        |org.jetbrains.plugins.scala.lang.psi.compiled.ScClsFileViewProvider$ScClsFileImpl
        |org.jetbrains.plugins.scala.lang.psi.compiled.ScClsFileViewProvider$ScClsFileImpl
        |org.jetbrains.plugins.scala.lang.psi.compiled.ScClsFileViewProvider$ScClsFileImpl
        |""".stripMargin.trim,
      filesClassNames.mkString("\n"),
    )

    //
    // Test that large decompiled files (scala and java) have stubs
    // This is the existing behavior copied from Java.
    // The test just fixates the current behavior.
    //
    val filesWithStubs: Seq[(PsiFile, PsiClassHolderFileStub[_])] = files
      .map { file =>
        val stub = file match {
          case cls: ScClsFileImpl => cls.getStub
          case cls: ClsFileImpl => cls.getStub
        }
        file -> stub
      }

    val fileNamesWithStubClasses: String =
      filesWithStubs
        .map { case (file, stub) =>
          s"${file.name} -> ${Option(stub).map(_.getClass.getName).orNull}"
        }
        .mkString("\n")
    assertEquals(
      "Decompiled scala and java class files should have non-null stubs",
      """Java_10KMethodsFromJar.class -> com.intellij.psi.impl.java.stubs.impl.PsiJavaFileStubImpl
        |Java_1KBigMethods10FromJar.class -> com.intellij.psi.impl.java.stubs.impl.PsiJavaFileStubImpl
        |Java_20KMethodsFromJar.class -> com.intellij.psi.impl.java.stubs.impl.PsiJavaFileStubImpl
        |Java_40KMethodsFromJar.class -> com.intellij.psi.impl.java.stubs.impl.PsiJavaFileStubImpl
        |Scala_10KMethodsFromJar.class -> org.jetbrains.plugins.scala.lang.psi.stubs.elements.ScStubFileElementType$ScFileStubImpl
        |Scala_1KBigMethods10FromJar.class -> org.jetbrains.plugins.scala.lang.psi.stubs.elements.ScStubFileElementType$ScFileStubImpl
        |Scala_20KMethodsFromJar.class -> org.jetbrains.plugins.scala.lang.psi.stubs.elements.ScStubFileElementType$ScFileStubImpl
        |Scala_40KMethodsFromJar.class -> org.jetbrains.plugins.scala.lang.psi.stubs.elements.ScStubFileElementType$ScFileStubImpl
        |""".stripMargin.trim,
      fileNamesWithStubClasses
    )
  }
}

object LargeFilesMixedPsiTests {

  def generateLargeScalaFileText: String = {
    val content = new StringBuilder()
    content.append(
      """package org.example.long.packageName.to.make.the.decompiled.version.larger.faster
        |
        |class DummyScalaClass {
        |""".stripMargin)

    val methodsCount = 50000
    (1 to methodsCount).foreach { idx =>
      content.append(s"def foo$idx: DummyScalaClass = ???\n")
    }

    content.append("\n}")
    content.toString()
  }

  /**
   * @see [[com.intellij.psi.SingleRootFileViewProvider.isTooLargeForIntelligence]]
   */
  def generateLargeScalaFileWithLargeCommentText: String = {
    val content = new StringBuilder()
    content.append("""class DummyScalaClass\n""")
    appendLargeBlockComment(content)
    content.toString()
  }

  private def appendLargeBlockComment(content: StringBuilder): Unit = {
    val commentLinesCount = 50000
    val commentLineLength = 100

    content.append("/*\n")
    for (_ <- 1 to commentLinesCount) {
      content.append(generateRandomComment(commentLineLength)).append("\n")
    }
    content.append("*/")
  }

  private def generateRandomComment(length: Int): String =
    Random.alphanumeric.take(length).mkString
}