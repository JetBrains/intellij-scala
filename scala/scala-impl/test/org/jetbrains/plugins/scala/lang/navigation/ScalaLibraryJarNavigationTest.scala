package org.jetbrains.plugins.scala.lang.navigation

import com.intellij.openapi.roots.{LibraryOrderEntry, ModuleRootManager, OrderRootType}
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.search.{FilenameIndex, GlobalSearchScope}
import com.intellij.psi.PsiManager
import org.jetbrains.plugins.scala.ScalaVersion
import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestCase
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.tasty.{TastyFileType, TastyReader}
import org.jetbrains.plugins.scala.tasty.reader.TreeReader
import org.jetbrains.org.objectweb.asm.{AnnotationVisitor, ClassReader, ClassVisitor, Opcodes}
import org.junit.Assert.{assertEquals, assertTrue}

import scala.jdk.CollectionConverters._

/**
 * Checks Scala library binary jars against their paired sources jars. The binary jar defines the
 * candidate inventory: every eligible class or TASTy file must be indexed, produce compiled Scala
 * PSI, and navigate to the corresponding indexed source file. Starting from the jar also detects
 * candidates that would be absent from an inventory built from indexed PSI.
 *
 * Related tests cover different levels of navigation and library content:
 * - [[ScalaSyntheticClassesNavigationalElementTestBase]] starts from discovered library PSI and
 *   checks navigation of definitions, members, and synthetic elements to source elements.
 * - [[ScalaCompiledExportNavigationTest_Scala_3_10]] and [[ScalaCompiledExportReviewNavigationTestBase]]
 *   check selected compiled exports and references against their exact source declarations.
 * - [[org.jetbrains.plugins.scala.corpus.scala2.ScalaLibraryTest]] and
 *   [[org.jetbrains.plugins.scala.corpus.scala3.ScalaLibraryTest]] compare decompiled declarations
 *   with stubs and source outlines, and check ScalaDoc in library sources.
 */
abstract class ScalaLibraryJarNavigationTestBase extends ScalaLightCodeInsightFixtureTestCase {

  override protected def includeScalaLibrarySources: Boolean = true

  private case class Candidate(file: VirtualFile, sourceName: Option[String])
  private case class Inventory(excluded: Vector[VirtualFile], candidates: Vector[Candidate])
  private case class Scala2Metadata(hasSignature: Boolean, sourceName: Option[String])

  private case class CheckResult(
    errors: Seq[String],
    binaryIndexed: Boolean,
    sourceIndexed: Boolean,
    sourceFound: Boolean,
    navigationExcluded: Boolean,
    navigationMatched: Boolean
  )

  private val ExpectedCandidateCounts = Map(
    ScalaVersion.Latest.Scala_2_10 -> 934,
    ScalaVersion.Latest.Scala_2_11 -> 738,
    ScalaVersion.Latest.Scala_2_12 -> 762,
    ScalaVersion.Latest.Scala_2_13 -> 802,
    ScalaVersion.Latest.Scala_3_3 -> 98,
    ScalaVersion.Latest.Scala_3_4 -> 105,
    ScalaVersion.Latest.Scala_3_5 -> 108,
    ScalaVersion.Latest.Scala_3_6 -> 109,
    ScalaVersion.Latest.Scala_3_7 -> 125,
    ScalaVersion.Latest.Scala_3_8 -> 941,
    ScalaVersion.Latest.Scala_3_9 -> 941,
    ScalaVersion.Latest.Scala_3_10 -> 941,
  )

  def testEveryLibraryCandidateIsIndexedAndNavigatesToItsSource(): Unit = {
    val jarName = libraryJarName
    val binaryRoot = attachedRoot(jarName, OrderRootType.CLASSES)
    val sourceRoot = attachedRoot(s"${jarName.stripSuffix(".jar")}-sources.jar", OrderRootType.SOURCES)

    // The binary jar, rather than a PSI or index query, determines which files must be checked.
    val inventory = if (version.isScala3) scala3Inventory(binaryRoot) else scala2Inventory(binaryRoot)
    val Inventory(excluded, candidates) = inventory
    assertTrue(s"No Scala candidates found in $jarName", candidates.nonEmpty)
    val expectedCount = ExpectedCandidateCounts.getOrElse(version,
      throw new AssertionError(s"Add the Scala ${version.minor} candidate count to ExpectedCandidateCounts"))
    assertEquals(s"Scala ${version.minor} $jarName candidate count changed; review the library and update " +
      "ExpectedCandidateCounts if the change is intended", expectedCount, candidates.size)

    // TreePrinter overflows on IsMap in Scala 3.8+; still check its binary and source indexing.
    val navigationExceptions = if (version.isScala3 && version >= ScalaVersion.Latest.Scala_3_8)
      Set("scala/collection/generic/IsMap.tasty")
    else
      Set.empty[String]
    val candidatePaths = candidates.map(_.file.getPath.stripPrefix(binaryRoot.getPath)).toSet
    assertTrue(s"Expected navigation exceptions missing from $jarName: ${navigationExceptions -- candidatePaths}",
      navigationExceptions.subsetOf(candidatePaths))

    val scope = GlobalSearchScope.allScope(getProject)
    val names = (candidates.map(_.file.getName) ++ filesRecursively(sourceRoot).map(_.getName)).distinct
    val indexedByName = names.iterator.map { name =>
      name -> FilenameIndex.getVirtualFilesByName(name, scope).asScala.toSet
    }.toMap
    val psiManager = PsiManager.getInstance(getProject)
    val classesWithTasty = if (version.isScala3 && version >= ScalaVersion.Latest.Scala_3_8)
      candidates.flatMap(candidate => Option(candidate.file.getParent.findChild(s"${candidate.file.getNameWithoutExtension}.class")))
    else
      Vector.empty[VirtualFile]
    if (version.isScala3 && version >= ScalaVersion.Latest.Scala_3_8)
      assertTrue(s"No .class files paired with TASTy in $jarName", classesWithTasty.nonEmpty)
    val duplicateScalaPsi = classesWithTasty.filter { file =>
      psiManager.findFile(file) match {
        case scalaFile: ScalaFile => scalaFile.isCompiled
        case _ => false
      }
    }
    assertTrue(s".class files paired with TASTy should not produce duplicate Scala PSI: ${duplicateScalaPsi.map(_.getPath)}",
      duplicateScalaPsi.isEmpty)

    val excludedWithScalaPsi = excluded.filter { file =>
      psiManager.findFile(file) match {
        case scalaFile: ScalaFile => scalaFile.isCompiled
        case _ => false
      }
    }
    assertTrue(s"Explicit exclusions now have compiled Scala PSI: ${excludedWithScalaPsi.map(_.getPath)}",
      excludedWithScalaPsi.isEmpty)

    val results = candidates.map(checkCandidate(_, binaryRoot, sourceRoot, indexedByName, psiManager, navigationExceptions))

    println(s"Scala ${version.minor} $jarName: ${candidates.size + excluded.size} inventory entries, " +
      s"${excluded.size} explicit exclusions, ${candidates.size} candidates, " +
      s"${results.count(_.binaryIndexed)} indexed binaries, ${results.count(_.sourceFound)} paired sources, " +
      s"${results.count(_.sourceIndexed)} indexed sources, ${results.count(_.navigationMatched)} correct navigation targets, " +
      s"${results.count(_.navigationExcluded)} navigation exceptions, ${classesWithTasty.size} paired .class files hidden")
    val failures = results.flatMap(_.errors)
    assertTrue(s"${failures.size} failures among ${candidates.size} candidates in $jarName:\n${failures.mkString("\n")}", failures.isEmpty)
  }

  private def libraryJarName: String = {
    // Since Scala 3.8, scala3-library_3 is an empty shell and TASTy lives in scala-library (SCL-24347).
    if (version.isScala3 && version < ScalaVersion.Latest.Scala_3_8)
      s"scala3-library_3-${version.minor}.jar"
    else
      s"scala-library-${version.minor}.jar"
  }

  private def scala3Inventory(binaryRoot: VirtualFile): Inventory = {
    val candidates = filesRecursively(binaryRoot)
      .filter(_.getExtension == TastyFileType.getDefaultExtension)
      .map { file =>
        val bytes = file.contentsToByteArray
        val (_, recordedSource) = TreeReader.treeAndSourceFileFrom(bytes)
        val sourceName = recordedSource.map(path => path.replace('\\', '/').split('/').last)
          .orElse(TastyReader.read(bytes).map(_._1))
        Candidate(file, sourceName)
      }
      .toVector
    Inventory(Vector.empty, candidates)
  }

  private def scala2Inventory(binaryRoot: VirtualFile): Inventory = {
    val signatureClasses = filesRecursively(binaryRoot)
      .filter(_.getExtension == "class")
      .map(file => file -> scala2Metadata(file))
      .filter(_._2.hasSignature)
      .toVector
    // Starting with Scala 2.12, these runtime classes carry ScalaSignature but are not Scala modules.
    val exceptions = if (version >= ScalaVersion.Latest.Scala_2_12)
      Set("scala/runtime/Null$.class", "scala/runtime/Nothing$.class")
    else
      Set.empty[String]
    val (ignored, eligible) = signatureClasses.partition { case (file, _) =>
      exceptions(file.getPath.stripPrefix(binaryRoot.getPath))
    }
    assertEquals("Scala 2 PSI exceptions should be reviewed when they change",
      exceptions, ignored.map(_._1.getPath.stripPrefix(binaryRoot.getPath)).toSet)
    Inventory(ignored.map(_._1), eligible.map { case (file, metadata) => Candidate(file, metadata.sourceName) })
  }

  private def checkCandidate(candidate: Candidate, binaryRoot: VirtualFile, sourceRoot: VirtualFile,
                             indexedByName: Map[String, Set[VirtualFile]], psiManager: PsiManager,
                             navigationExceptions: Set[String]): CheckResult = {
    val binaryIndexed = indexedByName.getOrElse(candidate.file.getName, Set.empty[VirtualFile]).contains(candidate.file)
    val binaryPath = candidate.file.getPath.stripPrefix(binaryRoot.getPath)
    val navigationExcluded = navigationExceptions(binaryPath)
    val sourcePath = candidate.sourceName.map(binaryPath.take(binaryPath.lastIndexOf('/') + 1) + _)
    val sourceFile = sourcePath.flatMap(path => Option(sourceRoot.findFileByRelativePath(path)))
    val sourceFound = sourceFile.nonEmpty
    val sourceIndexed = sourceFile.exists(file => indexedByName.getOrElse(file.getName, Set.empty[VirtualFile]).contains(file))
    val compiledPsi = psiManager.findFile(candidate.file) match {
      case file: ScalaFile if file.isCompiled => Some(file)
      case _ => None
    }
    val navigationFile = compiledPsi.flatMap(file => Option(file.getNavigationElement.getContainingFile))
      .flatMap(file => Option(file.getVirtualFile))
    val navigationMatched = sourceFile.exists(expected => navigationFile.contains(expected))
    val errors = Seq(
      Option.when(!binaryIndexed)(s"$binaryPath: binary file absent from FilenameIndex"),
      Option.when(candidate.sourceName.isEmpty)(s"$binaryPath: no recorded source filename"),
      Option.when(!sourceFound)(s"$binaryPath: source ${sourcePath.getOrElse("<unknown>")} absent from $sourceRoot"),
      Option.when(!sourceIndexed && sourceFound)(s"$binaryPath: source ${sourcePath.get} absent from FilenameIndex"),
      Option.when(navigationExcluded && compiledPsi.nonEmpty)(s"$binaryPath: compiled Scala PSI is now available; remove the navigation exception"),
      Option.when(!navigationExcluded && compiledPsi.isEmpty)(s"$binaryPath: no compiled Scala PSI"),
      Option.when(!navigationExcluded && !navigationMatched)(s"$binaryPath: navigation ${navigationFile.map(_.getPath).getOrElse("<none>")} != ${sourceFile.map(_.getPath).getOrElse("<missing source>")}")
    ).flatten
    CheckResult(errors, binaryIndexed, sourceIndexed, sourceFound, navigationExcluded, navigationMatched)
  }

  private def attachedRoot(jarName: String, rootType: OrderRootType): VirtualFile = {
    val roots = ModuleRootManager.getInstance(getModule).getOrderEntries.toSeq.collect {
      case entry: LibraryOrderEntry if entry.getLibrary != null => entry.getLibrary
    }.flatMap(_.getFiles(rootType)).distinctBy(_.getPath)
      .filter(_.getPath.endsWith(s"/$jarName!/"))
    assertEquals(s"Expected one attached $jarName $rootType root: ${roots.map(_.getPath)}", 1, roots.size)
    roots.head
  }

  private def filesRecursively(file: VirtualFile): Iterator[VirtualFile] =
    if (file.isDirectory) file.getChildren.iterator.flatMap(filesRecursively)
    else Iterator.single(file)

  private def scala2Metadata(file: VirtualFile): Scala2Metadata = {
    var hasSignature = false
    var sourceName = Option.empty[String]
    val visitor = new ClassVisitor(Opcodes.ASM9) {
      override def visitAnnotation(descriptor: String, visible: Boolean): AnnotationVisitor = {
        if (descriptor == "Lscala/reflect/ScalaSignature;" || descriptor == "Lscala/reflect/ScalaLongSignature;")
          hasSignature = true
        null
      }

      override def visitSource(source: String, debug: String): Unit = sourceName = Option(source)
    }
    new ClassReader(file.contentsToByteArray).accept(visitor,
      ClassReader.SKIP_CODE | ClassReader.SKIP_FRAMES)
    Scala2Metadata(hasSignature, sourceName)
  }
}

class ScalaLibraryJarNavigationTest_Scala_2_10 extends ScalaLibraryJarNavigationTestBase {
  override protected def supportedIn(version: ScalaVersion): Boolean = version == ScalaVersion.Latest.Scala_2_10
}

class ScalaLibraryJarNavigationTest_Scala_2_11 extends ScalaLibraryJarNavigationTestBase {
  override protected def supportedIn(version: ScalaVersion): Boolean = version == ScalaVersion.Latest.Scala_2_11
}

class ScalaLibraryJarNavigationTest_Scala_2_12 extends ScalaLibraryJarNavigationTestBase {
  override protected def supportedIn(version: ScalaVersion): Boolean = version == ScalaVersion.Latest.Scala_2_12
}

class ScalaLibraryJarNavigationTest_Scala_2_13 extends ScalaLibraryJarNavigationTestBase {
  override protected def supportedIn(version: ScalaVersion): Boolean = version == ScalaVersion.Latest.Scala_2_13
}

class ScalaLibraryJarNavigationTest_Scala_3_3 extends ScalaLibraryJarNavigationTestBase {
  override protected def supportedIn(version: ScalaVersion): Boolean = version == ScalaVersion.Latest.Scala_3_3
}

class ScalaLibraryJarNavigationTest_Scala_3_4 extends ScalaLibraryJarNavigationTestBase {
  override protected def supportedIn(version: ScalaVersion): Boolean = version == ScalaVersion.Latest.Scala_3_4
}

class ScalaLibraryJarNavigationTest_Scala_3_5 extends ScalaLibraryJarNavigationTestBase {
  override protected def supportedIn(version: ScalaVersion): Boolean = version == ScalaVersion.Latest.Scala_3_5
}

class ScalaLibraryJarNavigationTest_Scala_3_6 extends ScalaLibraryJarNavigationTestBase {
  override protected def supportedIn(version: ScalaVersion): Boolean = version == ScalaVersion.Latest.Scala_3_6
}

class ScalaLibraryJarNavigationTest_Scala_3_7 extends ScalaLibraryJarNavigationTestBase {
  override protected def supportedIn(version: ScalaVersion): Boolean = version == ScalaVersion.Latest.Scala_3_7
}

class ScalaLibraryJarNavigationTest_Scala_3_8 extends ScalaLibraryJarNavigationTestBase {
  override protected def supportedIn(version: ScalaVersion): Boolean = version == ScalaVersion.Latest.Scala_3_8
}

class ScalaLibraryJarNavigationTest_Scala_3_9 extends ScalaLibraryJarNavigationTestBase {
  override protected def supportedIn(version: ScalaVersion): Boolean = version == ScalaVersion.Latest.Scala_3_9
}

class ScalaLibraryJarNavigationTest_Scala_3_10 extends ScalaLibraryJarNavigationTestBase {
  override protected def supportedIn(version: ScalaVersion): Boolean = version == ScalaVersion.Latest.Scala_3_10
}
