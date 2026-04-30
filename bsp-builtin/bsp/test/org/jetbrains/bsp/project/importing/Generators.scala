package org.jetbrains.bsp.project.importing

import ch.epfl.scala.bsp.testkit.gen.Bsp4jGenerators._
import ch.epfl.scala.bsp.testkit.gen.UtilGenerators.{genFileUriString, genPath}
import ch.epfl.scala.bsp.testkit.gen.bsp4jArbitrary._
import ch.epfl.scala.bsp4j.{BuildTarget, BuildTargetIdentifier}
import com.google.gson.{Gson, GsonBuilder}
import com.intellij.pom.java.LanguageLevel
import org.jetbrains.bsp.data.{JdkData, ScalaSdkData}
import org.jetbrains.bsp.project.importing.BspResolverDescriptors._
import org.jetbrains.plugins.scala.extensions.PathExt
import org.jetbrains.sbt.project.data.MyURI
import com.intellij.platform.eel.EelDescriptor
import com.intellij.platform.eel.provider.LocalEelDescriptor
import org.jetbrains.sbt.project.structure.data.{InterpretablePath, PathConstructor}
import org.jetbrains.sbt.project.toPath
import org.scalacheck.Arbitrary.arbitrary
import org.scalacheck.{Arbitrary, Gen}

import java.nio.file.{Path, Paths}
import scala.jdk.CollectionConverters._

object Generators {

  private given EelDescriptor = LocalEelDescriptor.INSTANCE

  given PathConstructor[Path]:
    override def construct(path: Path): InterpretablePath =
      new InterpretablePath(path.toCanonicalPath.toString)

  implicit val gson: Gson = new GsonBuilder().setPrettyPrinting().create()

  implicit val arbModuleKind: Arbitrary[ModuleKind] = Arbitrary(genModuleKind)
  implicit val arbModuleDescription: Arbitrary[ModuleDescription] = Arbitrary(genModuleDescription)
  implicit val arbVersion: Arbitrary[String] = Arbitrary(genVersion)
  implicit val arbPath: Arbitrary[Path] = Arbitrary(genPath)
  implicit val arbLanguageLevel: Arbitrary[LanguageLevel] = Arbitrary(genLanguageLevel)
  implicit val arbSourceDirectory: Arbitrary[SourceEntry] = Arbitrary(genSourceDirectory)

  /** A system-dependent file path. */
  def genPathBelow(root: Path): Gen[Path] = for {
    segmentCount <- Gen.choose(0, 10)
    segments <- Gen.listOfN(segmentCount, Gen.identifier)
  } yield {
    // truncate string in case it's too long for macOS file system paths
    val combined = segments.mkString("/")
    val toTruncate = 250 - root.toString.length
    val truncated =
      if (toTruncate < combined.length) combined.substring(0, toTruncate)
      else combined
    val sub = Paths.get(truncated)
    root.resolve(sub)
  }

  def genLanguageLevel: Gen[LanguageLevel] = for {
     n <- Gen.chooseNum(0, LanguageLevel.values().length - 1)
     value = LanguageLevel.values()(n)
  } yield value

  def genVersion: Gen[String] = for {
    n <- Gen.chooseNum(0,4)
    v <- Gen.listOfN(n, Gen.posNum[Int])
    s <- Gen.identifier.optional
  } yield v.mkString(".") + s.fold("")("-" + _)

  def genSourceDirectoryUnder(root: Path): Gen[SourceEntry] = for {
    path <- genPathBelow(root)
    generated <- arbitrary[Boolean]
  } yield SourceEntry(InterpretablePath.construct(path), isDirectory = true, generated, None)

  def genSourceDirectory: Gen[SourceEntry] = for {
    path <- arbitrary[Path]
    generated <- arbitrary[Boolean]
  } yield SourceEntry(InterpretablePath.construct(path), isDirectory = true, generated = generated, None)

  def genSourceDirs(root: Option[InterpretablePath]): Gen[List[SourceEntry]] = Gen.sized { size =>
    for {
      size1 <- Gen.choose(0,size)
      size2 = size - size1
      free <- Gen.listOfN(size1, genSourceDirectory)
      underRoot <- root
        .map(p => Gen.listOfN(size2, genSourceDirectoryUnder(p.toPath)))
        .getOrElse(Gen.const[List[SourceEntry]](List.empty))
    } yield {
      free ++ underRoot
    }
  }

  def genScalaBuildTargetWithoutTags(withoutTags: List[String]): Gen[BuildTarget] = for {
    target <- genBuildTargetWithScala
    baseDir <- genFileUriString
  } yield {
    val newTags = target.getTags.asScala.filterNot(withoutTags.contains)
    target.setTags(newTags.asJava)
    if (target.getBaseDirectory == null)
      target.setBaseDirectory(baseDir)
    target
  }

  def genScalaSdkData: Gen[ScalaSdkData] = for {
    scalaOrganization <- arbitrary[String]
    scalaVersion <- arbitrary[Option[String]]
    scalacClasspath <- Gen.listOf(arbitrary[Path])
    scaladocExtraClasspath <- Gen.listOf(arbitrary[Path])
    scalacOptions <- Gen.listOf(arbitrary[String])
  } yield ScalaSdkData(scalaOrganization, scalaVersion.orNull, scalacClasspath, scaladocExtraClasspath, scalacOptions)

  def genJdkData: Gen[JdkData] = for {
    javaHome <- genFileUri
    javaVersion <- arbitrary[String]
  } yield JdkData(new MyURI(javaHome), javaVersion)

  def genModuleKind: Gen[ModuleKind] = for {
    jdkData <- genJdkData
    scalaSdkData <- genScalaSdkData
  } yield ModuleKind.ScalaModule(jdkData, scalaSdkData)

  def genModuleDescription: Gen[ModuleDescription] = for {
    id <- arbitrary[String]
    name <- arbitrary[String]
    targets <- arbitrary[List[BuildTarget]]
    targetDependencies <- arbitrary[Seq[BuildTargetIdentifier]]
    targetTestDependencies <- arbitrary[Seq[BuildTargetIdentifier]]
    basePath <- arbitrary[Path].map(InterpretablePath.construct).optional
    output <- arbitrary[Option[Path]].map(_.map(InterpretablePath.construct))
    testOutput <- arbitrary[Option[Path]].map(_.map(InterpretablePath.construct))
    sourceDirs <- genSourceDirs(basePath)
    testSourceDirs <- genSourceDirs(basePath)
    resourceDirs <- genSourceDirs(basePath)
    testResourceDirs <- genSourceDirs(basePath)
    classPath <- arbitrary[Seq[Path]].map(_.map(InterpretablePath.construct))
    classPathSources <- arbitrary[Seq[Path]].map(_.map(InterpretablePath.construct))
    testClassPath <- arbitrary[Seq[Path]].map(_.map(InterpretablePath.construct))
    testClassPathSources <- arbitrary[Seq[Path]].map(_.map(InterpretablePath.construct))
    outputPaths <- arbitrary[Seq[Path]].map(_.map(InterpretablePath.construct))
    moduleKind <- genModuleKind
  } yield {
    val data = ModuleDescriptionData(
      idUri = id,
      name = name,
      targets = targets,
      targetDependencies = targetDependencies,
      targetTestDependencies = targetTestDependencies,
      basePath = basePath,
      output = output, testOutput = testOutput,
      sourceRoots = sourceDirs,
      testSourceRoots = testSourceDirs,
      resourceRoots = resourceDirs,
      testResourceRoots = testResourceDirs,
      outputPaths = outputPaths,
      classpath = classPath,
      classpathSources = classPathSources,
      testClasspath = testClassPath,
      testClasspathSources = testClassPathSources,
      languageLevel = None
    )
    ModuleDescription(data, moduleKind)
  }

}
