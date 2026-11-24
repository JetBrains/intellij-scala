package org.jetbrains.plugins.scala.semantic

import com.intellij.openapi.diff.impl.patch.{TextFilePatch, TextPatchBuilder, UnifiedDiffWriter}
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.{PsiClass, PsiComment, PsiElement, PsiWhiteSpace}
import com.intellij.util.concurrency.AppExecutorUtil
import org.jetbrains.plugins.scala.DependencyManagerBase.DependencyDescription
import org.jetbrains.plugins.scala.corpus.scala3.Scala3ProjectCorpusTestDef
import org.jetbrains.plugins.scala.corpus.{ProjectCorpusTestBase, ProjectCorpusTestDef}
import org.jetbrains.plugins.scala.extensions.{ObjectExt, PsiElementExt, inReadAction}
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScPackaging
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports.ScImportStmt
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTypeDefinition
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiManager
import org.jetbrains.plugins.scala.project.settings.ScalaCompilerConfiguration
import org.jetbrains.plugins.scala.semantic.SemanticTestBase.{decompilerClassLoader, definition}
import org.jetbrains.plugins.scala.settings.ScalaApplicationSettings
import org.jetbrains.plugins.scala.{LatestScalaVersions, ScalaVersion, SemanticTests}
import org.junit.Assert
import org.junit.experimental.categories.Category

import java.io.StringWriter
import java.nio.file.{Files, Path}
import java.util.concurrent.ExecutionException
import scala.concurrent.duration.DurationInt
import scala.concurrent.{Await, ExecutionContext, Future}

@Category(Array(classOf[SemanticTests]))
abstract class SemanticTestBase(dependencies: DependencyDescription*)(packages: String*) extends ProjectCorpusTestBase(definition(dependencies, packages)) {
  private enum Mode {
    case Test      // Test listed cases
    case Elaborate // Find and elaborate classes to ../semantic-data, update listed cases in test source file
  }

  private val mode: Mode =
    if (System.getenv("SEMANTIC_TEST_MODE") == "Elaborate") Mode.Elaborate
    else Mode.Test

  private val DataDirectory = Path.of("..", "semantic-data")
  private val CompilerSuffix = "-compiler"
  private val PluginSuffix = "-plugin"
  private val TestDirectory = Path.of("scala", "scala-impl", "test")

  override def runInDispatchThread(): Boolean = false

  protected def enableKindProjectorPlugin: Boolean = false

  override protected def setUp(): Unit = {
    super.setUp()
    val settings = ScalaApplicationSettings.getInstance()
    settings.PRECISE_TEXT = true
    settings.PRECISE_TEXT_FOR_TYPE_PARAMETERS = true
    if (enableKindProjectorPlugin) {
      val profile = ScalaCompilerConfiguration.instanceIn(getProject).defaultProfile
      profile.setSettings(profile.getSettings.copy(additionalCompilerOptions = Seq("-Ykind-projector")))
    }
  }

  override def tearDown(): Unit = try {
    val settings = ScalaApplicationSettings.getInstance()
    settings.PRECISE_TEXT = false
    settings.PRECISE_TEXT_FOR_TYPE_PARAMETERS = false
  } finally {
    super.tearDown()
  }

  protected def doTest(classes: String): Unit = {
    val numBatches = Runtime.getRuntime.availableProcessors

    val classpath =
      ModuleRootManager.getInstance(getMyFixture.getModule)
        .orderEntries.productionOnly.librariesOnly.classes.getRoots.toSeq
        .map(virtualFile => VfsUtil.getLocalFile(virtualFile).getPath)

    implicit val executionContext: ExecutionContext = ExecutionContext.fromExecutorService(AppExecutorUtil.getAppExecutorService)

    val allTestCases = mode match {
      case Mode.Test =>      classes.split('\n').map(_.trim).filterNot(_.isEmpty).toSeq // Listed cases
      case Mode.Elaborate => inReadAction(allClasses(excludePackages = Set.empty).map(_.qualifiedName).distinct) // Find classes
    }

    val total = allTestCases.length
    mode match {
      case Mode.Test =>      println(s"Testing $total cases:")
      case Mode.Elaborate => println(s"Elaborating $total classes to ${DataDirectory.toAbsolutePath.normalize}:")
    }

    var n = 0
    val futures = splitInto(numBatches, allTestCases).map { testCases =>
      Future {
        val decompiler = Decompiler(classpath, decompilerClassLoader)

        var foundTestCases = List.empty[String]

        testCases.foreach { testCase =>
          synchronized { println(f"${n += 1; n}%04d/$total%s: $testCase") }
          val isCommented = testCase.startsWith("//")
          val fqn = if (isCommented) testCase.substring(2) else testCase

          val cls = inReadAction(ScalaPsiManager.instance(getProject).getCachedClass(GlobalSearchScope.allScope(getProject), fqn))
            .getOrElse(throw new IllegalArgumentException("Cannot find class: " + fqn)).asInstanceOf[ScTypeDefinition]

          mode match {
            case Mode.Test => // Test listed cases
              val (compilerText, pluginText) = {
                def result: (String, String) = textOf(cls, decompiler) { (compilerText, pluginText) =>
                  if (isCommented && (compilerText.length < pluginText.length || CharSequence.compare(compilerText.subSequence(0, pluginText.length), pluginText) != 0)) {
                    return (compilerText.toString, pluginText.toString) // Partial result (non-local return)
                  }
                }
                result
              }
              if (isCommented) {
                Assert.assertNotEquals(s"Expected to contain differences: $fqn", compilerText, pluginText)
              } else {
                Assert.assertEquals(s"$fqn [compiler | plugin]", compilerText, pluginText)
              }
            case Mode.Elaborate => // Elaborate found classes to ../semantic-data
              try {
                val (compilerText, pluginText) = textOf(cls, decompiler)((_, _) => ()) // Full result
                foundTestCases ::= (if (compilerText != pluginText) "//" else "") + fqn
                val sourceText = inReadAction {
                  val sourceClass = cls.getSourceMirrorClass.asInstanceOf[ScTypeDefinition]
                  val header = {
                    val isHeaderElement = (_: PsiElement).is[PsiComment, PsiWhiteSpace, ScPackaging, ScImportStmt]
                    def headerIn(e: PsiElement): Iterator[PsiElement] = e match {
                      case f: ScalaFile => f.children.takeWhile(isHeaderElement).flatMap(headerIn)
                      case p: ScPackaging => p.children.take(3) ++ p.children.drop(3).takeWhile(isHeaderElement).flatMap(headerIn)
                      case e => Iterator(e)
                    }
                    val sourceFile = sourceClass.getContainingFile.asInstanceOf[ScalaFile]
                    headerIn(sourceFile).map(_.getText).mkString.stripSuffix("\n")
                  }
                  val body = sourceClass.getText + sourceClass.baseCompanionTypeDefinition.map("\n\n" + _.getText).getOrElse("")
                  header + body
                }
                val packageDirectory = fqn.split('.').dropRight(1).foldLeft(DataDirectory)(_.resolve(_))
                Files.createDirectories(packageDirectory)
                val javaClassName = inReadAction((cls: PsiClass).getName).stripSuffix("$")
                Files.write(packageDirectory.resolve(s"$javaClassName.scala"), sourceText.getBytes)
                Files.write(packageDirectory.resolve(s"$javaClassName$CompilerSuffix.scala"), compilerText.getBytes)
                val pluginFile = packageDirectory.resolve(s"$javaClassName$PluginSuffix.scala")
                val diffFile = packageDirectory.resolve(s"$javaClassName.diff")
                if (pluginText != compilerText) {
                  Files.write(pluginFile, pluginText.getBytes)
                  val diff = formatDiff(s"$javaClassName$CompilerSuffix.scala", s"$javaClassName$PluginSuffix.scala", compilerText, pluginText)
                  Files.write(diffFile, diff.getBytes)
                } else {
                  Files.deleteIfExists(pluginFile)
                  Files.deleteIfExists(diffFile)
                }
              } catch {
                case e: Throwable => System.err.println(fqn + ": " + e.getMessage) // Ignore classes with errors
              }
          }
        }

        foundTestCases
      }
    }

    val foundTestCases = try {
      Await.result(Future.sequence(futures), 10.minutes).flatten
    } catch {
      case e: ExecutionException => throw e.getCause
    }

    mode match {
      case Mode.Test =>
      case Mode.Elaborate => // Update listed cases in test source file
        val sourceFile = (getClass.getPackageName.split('.').toSeq :+ (getClass.getSimpleName + ".scala")).foldLeft(TestDirectory)(_.resolve(_))
        Assert.assertTrue(s"Test source not found: ${sourceFile.toString}", Files.exists(sourceFile))
        val contents = Files.readString(sourceFile)
        val ContentsPattern = "(?s)(.*?\"\"\"\n).*(\n\\s*\"\"\".*?)".r
        contents match {
          case ContentsPattern(prefix, suffix) =>
            val uncommented = foundTestCases.filterNot(_.startsWith("//")).toSet
            val testCases = foundTestCases.filterNot(s => s.startsWith("//") && uncommented(s.substring(2))).sortBy(_.stripPrefix("//")).map("    " + _).mkString("\n")
            Files.write(sourceFile, (prefix + testCases + suffix).getBytes)
          case _ =>
            Assert.fail(s"Cannot find placeholder for test cases: ${sourceFile.toString}")
        }
    }
  }

  //noinspection ApiStatus
  private def formatDiff(name1: String, name2: String, text1: String, text2: String): String = {
    val patch = new TextFilePatch(null, "\n")
    patch.setBeforeName(name1)
    patch.setAfterName(name2)
    TextPatchBuilder.buildPatchHunks(text1, text2).forEach(patch.addHunk(_))

    val writer = new StringWriter()
    writer.append("--- ").append(name1).append('\n')
    writer.append("+++ ").append(name2).append('\n')
    UnifiedDiffWriter.writeHunk(writer, patch, "\n", "\n")
    writer.toString
  }

  private def textOf(cls: ScTypeDefinition, decompiler: Decompiler)(listener: (CharSequence, CharSequence) => Unit): (String, String) = {
    val tastyFile = cls.getContainingFile.getVirtualFile
    Assert.assertTrue(tastyFile.getName, tastyFile.getExtension == "tasty")

    val decompiledText = decompiler.decompile(tastyFile.getName, tastyFile.contentsToByteArray().toIndexedSeq)

    val sourceCls = inReadAction(cls.getSourceMirrorClass).asInstanceOf[ScTypeDefinition]
    val qualifiedName = inReadAction(cls.qualifiedName)
    Assert.assertTrue(s"Must have a source: $qualifiedName", sourceCls != cls)
    Assert.assertFalse(s"Must be in a source file: $qualifiedName", sourceCls.isInCompiledFile)

    val psiText = inReadAction {
      sourceCls.getText // Necessary to load right-hand sides
      ClassPrinter.textOf(sourceCls, isScala3 = true, listener(decompiledText, _))
    }

    (decompiledText + "\n", psiText + "\n")
  }

  private def splitInto[A](numBatches: Int, collection: Seq[A]): Seq[Seq[A]] = {
    require(numBatches > 0, "Number of batches must be greater than 0")

    val (quot, rem) = (collection.size / numBatches, collection.size % numBatches)

    // The first `rem` batches get one extra element (quot + 1)
    val (larger, smaller) = collection.splitAt(rem * (quot + 1))

    val largerBatches = if (quot + 1 > 0) larger.grouped(quot + 1) else Iterator.empty
    val smallerBatches = if (quot > 0) smaller.grouped(quot) else Iterator.empty

    // Combine and pad with empty Seqs in case collection.size < numBatches
    (largerBatches ++ smallerBatches).toSeq.padTo(numBatches, Seq.empty[A])
  }
}

object SemanticTestBase {
  implicit val scalaVersion: ScalaVersion = LatestScalaVersions.Scala_3

  private def definition(_dependencies: Seq[DependencyDescription], _packages: Seq[String]): ProjectCorpusTestDef = new Scala3ProjectCorpusTestDef() {
    override val dependencies: Seq[DependencyDescription] = _dependencies
    override val packages: Seq[String] = _packages
    override val includeScalaReflect: Boolean = false
    override val includeScalaCompiler: Boolean = false
  }

  private lazy val decompilerClassLoader = Decompiler.classLoader(getClass.getClassLoader)
}