package org.jetbrains.plugins.scala.semantic

import com.intellij.openapi.diff.impl.patch.{TextFilePatch, TextPatchBuilder, UnifiedDiffWriter}
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.util.concurrency.AppExecutorUtil
import org.jetbrains.plugins.scala.DependencyManagerBase.DependencyDescription
import org.jetbrains.plugins.scala.corpus.scala3.Scala3ProjectCorpusTestDef
import org.jetbrains.plugins.scala.corpus.{ProjectCorpusTestBase, ProjectCorpusTestDef}
import org.jetbrains.plugins.scala.extensions.inReadAction
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTypeDefinition
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiManager
import org.jetbrains.plugins.scala.lang.refactoring.util.ScalaNamesUtil.toJavaName
import org.jetbrains.plugins.scala.project.settings.ScalaCompilerConfiguration
import org.jetbrains.plugins.scala.semantic.SemanticTestBase.{decompilerClassLoader, definition}
import org.jetbrains.plugins.scala.settings.ScalaApplicationSettings
import org.jetbrains.plugins.scala.{LatestScalaVersions, ScalaVersion, SemanticTests}
import org.junit.Assert
import org.junit.experimental.categories.Category

import java.io.StringWriter
import java.nio.file.{Files, Path}
import scala.concurrent.duration.DurationInt
import scala.concurrent.{Await, ExecutionContext, Future}

@Category(Array(classOf[SemanticTests]))
abstract class SemanticTestBase(dependencies: DependencyDescription*)(packages: String*) extends ProjectCorpusTestBase(definition(dependencies, packages)) {
  private val Print =
//    true // Print found cases to target/comparison and update the test source file
    false // Test provided cases

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
    val numBatches = 8

    val classpath =
      ModuleRootManager.getInstance(getMyFixture.getModule)
        .orderEntries.productionOnly.librariesOnly.classes.getRoots.toSeq
        .map(virtualFile => VfsUtil.getLocalFile(virtualFile).getPath)

    implicit val executionContext: ExecutionContext = ExecutionContext.fromExecutorService(AppExecutorUtil.getAppExecutorService)

    val classNames =
      if (Print) inReadAction(allClasses(excludePackages = Set.empty).map(_.qualifiedName))
      else classes.split('\n').map(_.trim).filterNot(_.isEmpty).toSeq

    val futures = splitInto(numBatches, classNames).map { classes =>
      Future {
        val decompiler = Decompiler(classpath, decompilerClassLoader)

        var results = List.empty[String]

        classes.foreach { name =>
          val isCommented = name.startsWith("//")
          val fqn = if (isCommented) name.substring(2) else name

          val cls = inReadAction {
            ScalaPsiManager.instance(getProject).getCachedClass(GlobalSearchScope.allScope(getProject), fqn)
          }.getOrElse(throw new IllegalArgumentException(fqn)).asInstanceOf[ScTypeDefinition]

          try {
            val (decompiledText, psiText) = {
              def result: (String, String) = textOf(cls, decompiler) { (decompiledText, psiText) =>
                if (!Print && isCommented && (decompiledText.length < psiText.length || CharSequence.compare(decompiledText.subSequence(0, psiText.length), psiText) != 0)) {
                  return (decompiledText.toString, psiText.toString) // Partial result (non-local return)
                }
              }
              result
            }

            // Print found cases to target/comparison
            if (Print) {
              results ::= (if (decompiledText != psiText) "//" else "") + fqn
              val sourceText = inReadAction {
                val sourceClass = cls.getSourceMirrorClass.asInstanceOf[ScTypeDefinition]
                sourceClass.getText + sourceClass.baseCompanionTypeDefinition.map("\n\n" + _.getText).getOrElse("")
              }
              val directory = Path.of("scala", Seq("scala-impl", "target", "comparison") ++ fqn.split('.').dropRight(1): _*)
              Files.createDirectories(directory)
              val fileName = toJavaName(cls.name)
              Files.write(directory.resolve(fileName + ".scala"), sourceText.getBytes)
              Files.write(directory.resolve(fileName + "1.scala"), decompiledText.getBytes)
              val file2 = directory.resolve(fileName + "2.scala")
              val diffFile = directory.resolve(fileName + ".diff")
              if (psiText != decompiledText) {
                Files.write(file2, psiText.getBytes)
                val diff = formatDiff(fileName + "1.scala", fileName + "2.scala", decompiledText, psiText)
                Files.write(diffFile, diff.getBytes)
              } else {
                Files.deleteIfExists(file2)
                Files.deleteIfExists(diffFile)
              }
            } else {
              if (isCommented) {
                Assert.assertNotEquals(s"Expected to contain differences: $fqn", decompiledText, psiText)
              } else {
                Assert.assertEquals(s"$fqn [compiler | plugin]", decompiledText, psiText)
              }
            }
          } catch {
            case e: Throwable if Print => System.err.println(fqn + ": " + e.getMessage)
          }
        }

        results
      }
    }

    val results = Await.result(Future.sequence(futures), 10.minutes).flatten

    // Update the test source file
    if (Print) {
      val sourceFile = Path.of("scala", Seq("scala-impl", "test") ++ getClass.getPackageName.split('.').toSeq :+ (getClass.getSimpleName + ".scala"): _*)
      Assert.assertTrue(s"Test source not found: ${sourceFile.toString}", Files.exists(sourceFile))
      val contents = Files.readString(sourceFile)
      val ContentsPattern = "(?s)(.*?\"\"\"\n).*(\n\\s*\"\"\".*?)".r
      contents match {
        case ContentsPattern(prefix, suffix) =>
          val testCases = results.sortBy(_.stripPrefix("//")).map("    " + _).mkString("\n")
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

    val decompiledText = decompiler.decompile(tastyFile.getName, tastyFile.contentsToByteArray())

    val sourceCls = inReadAction(cls.getSourceMirrorClass).asInstanceOf[ScTypeDefinition]
    val qualifiedName = inReadAction(cls.qualifiedName)
    Assert.assertTrue(s"Must have a source: $qualifiedName", sourceCls != cls)
    Assert.assertFalse(s"Must be in a source file: $qualifiedName", sourceCls.isInCompiledFile)

    val psiText = inReadAction {
      sourceCls.getText // Necessary to load right-hand sides
      ClassPrinter.textOf(sourceCls, listener(decompiledText, _))
    }

    (decompiledText, psiText)
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