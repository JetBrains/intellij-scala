package org.jetbrains.plugins.scala.semantic

import com.intellij.openapi.diff.impl.patch.{TextFilePatch, TextPatchBuilder, UnifiedDiffWriter}
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.util.concurrency.AppExecutorUtil
import org.jetbrains.plugins.scala.SemanticTests
import org.jetbrains.plugins.scala.corpus.{ProjectCorpusTestBase, ProjectCorpusTestDef}
import org.jetbrains.plugins.scala.extensions.inReadAction
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTypeDefinition
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiManager
import org.jetbrains.plugins.scala.settings.ScalaApplicationSettings
import org.junit.Assert
import org.junit.experimental.categories.Category

import java.io.StringWriter
import java.nio.file.{Files, Path}
import scala.concurrent.duration.DurationInt
import scala.concurrent.{Await, ExecutionContext, Future}

@Category(Array(classOf[SemanticTests]))
abstract class SemanticTestBase(config: ProjectCorpusTestDef) extends ProjectCorpusTestBase(config) {
  private val Print =
    //    true // Print actual cases and save contents to target/comparison/
    false // Test expected cases

  override def runInDispatchThread(): Boolean = false

  override protected def setUp(): Unit = {
    super.setUp()
    val settings = ScalaApplicationSettings.getInstance()
    settings.PRECISE_TEXT = true
    settings.PRECISE_TEXT_FOR_TYPE_PARAMETERS = true
  }

  override def tearDown(): Unit = try {
    val settings = ScalaApplicationSettings.getInstance()
    settings.PRECISE_TEXT = false
    settings.PRECISE_TEXT_FOR_TYPE_PARAMETERS = false
  } finally {
    super.tearDown()
  }

  protected def doTest(classes: String): Unit = {
    val batchSize = 8

    val executor = AppExecutorUtil.createBoundedApplicationPoolExecutor(
      "SemanticTest",
      AppExecutorUtil.getAppExecutorService,
      batchSize,
      getTestRootDisposable
    )

    given ExecutionContext = ExecutionContext.fromExecutorService(executor)

    val classNames =
      if (Print) allClasses(excludePackages = Set.empty).map(_.qualifiedName)
      else classes.split('\n').map(_.trim).filterNot(_.isEmpty).toSeq

    val futures = splitInto(batchSize, classNames).map { classes =>
      Future {
        val decompiler: Decompiler = {
          val classpath =
            ModuleRootManager.getInstance(getMyFixture.getModule)
              .orderEntries.productionOnly.librariesOnly.classes.getRoots.toSeq
              .map(virtualFile => VfsUtil.getLocalFile(virtualFile).getPath)
          new Decompiler(classpath)
        }

        classes.foreach { name =>
          val isCommented = name.startsWith("//")
          val fqn = if (isCommented) name.substring(2) else name

          val cls = inReadAction {
            ScalaPsiManager.instance(getProject).getCachedClass(GlobalSearchScope.allScope(getProject), fqn)
          }.getOrElse(throw new IllegalArgumentException(fqn)).asInstanceOf[ScTypeDefinition]

          try {
            val (decompiledText, psiText) = textOf(cls, decompiler)

            if (Print) {
              val comment = decompiledText != psiText
              println((if (comment) "//" else "") + fqn)
              val sourceText = {
                val sourceClass = cls.getSourceMirrorClass.asInstanceOf[ScTypeDefinition]
                sourceClass.getText + sourceClass.baseCompanionTypeDefinition.map("\n\n" + _.getText).getOrElse("")
              }
              val directory = Path.of("scala", Seq("semantic-tests", "target", "comparison") ++ fqn.split('.').dropRight(1)*)
              Files.createDirectories(directory)
              Files.write(directory.resolve(cls.name + ".scala"), sourceText.getBytes)
              Files.write(directory.resolve(cls.name + "1.scala"), decompiledText.getBytes)
              val file2 = directory.resolve(cls.name + "2.scala")
              val diffFile = directory.resolve(cls.name + ".diff")
              if (psiText != decompiledText) {
                Files.write(file2, psiText.getBytes)
                val diff = formatDiff(cls.name + "1.scala", cls.name + "2.scala", decompiledText, psiText)
                Files.write(diffFile, diff.getBytes)
              } else {
                Files.deleteIfExists(file2)
                Files.deleteIfExists(diffFile)
              }
            } else {
              println(fqn)
              if (isCommented) {
                Assert.assertNotEquals(fqn, decompiledText, psiText)
              } else {
                Assert.assertEquals(fqn, decompiledText, psiText)
              }
            }
          } catch {
            case e: Throwable if Print => System.err.println(fqn + ": " + e.getMessage)
          }
        }
      }
    }

    val join = Future.sequence(futures).map(_ => ())
    Await.result(join, 10.minutes)
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

  private def textOf(cls: ScTypeDefinition, decompiler: Decompiler): (String, String) = {
    val sourceCls = inReadAction(cls.getSourceMirrorClass).asInstanceOf[ScTypeDefinition]
    Assert.assertTrue(s"Must have a source: ${cls.qualifiedName}", sourceCls != cls)
    Assert.assertFalse(s"Must be in a source file: ${cls.qualifiedName}", sourceCls.isInCompiledFile)

    val psiText = inReadAction {
      sourceCls.getText // Necessary to load right-hand sides
      ClassPrinter.textOf(sourceCls)
    }

    val tastyFile = cls.getContainingFile.getVirtualFile
    Assert.assertTrue(tastyFile.getName, tastyFile.getExtension == "tasty")

    val decompiledText = decompiler.decompile(tastyFile.getName, tastyFile.contentsToByteArray())

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
