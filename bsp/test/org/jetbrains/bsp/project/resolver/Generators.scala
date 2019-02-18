package org.jetbrains.bsp.project.resolver

import java.io.File
import java.nio.file.{Path, Paths}

import ch.epfl.scala.bsp.testkit.gen.Bsp4jGenerators._
import ch.epfl.scala.bsp.testkit.gen.bsp4jArbitrary._
import ch.epfl.scala.bsp.testkit.gen.UtilGenerators.{genFileUriString, genPath}
import ch.epfl.scala.bsp4j.{BuildTarget, BuildTargetIdentifier}
import com.google.gson.{Gson, GsonBuilder}
import org.jetbrains.bsp.project.resolver.BspResolverDescriptors.{ModuleDescription, SourceDirectory}
import org.scalacheck.Arbitrary.arbitrary
import org.scalacheck.{Arbitrary, Gen}

import scala.collection.JavaConverters._
import BspResolverDescriptors._
import org.jetbrains.bsp.data.ScalaSdkData
import org.jetbrains.plugins.scala.project.Version

object Generators {

  implicit val gson: Gson = new GsonBuilder().setPrettyPrinting().create()

  implicit val arbFile: Arbitrary[File] = Arbitrary(genPath.map(_.toFile))
  implicit val arbModuleKind: Arbitrary[ModuleKind] = Arbitrary(genModuleKind)
  implicit val arbModuleDescription: Arbitrary[ModuleDescription] = Arbitrary(genModuleDescription)
  implicit val arbVersion: Arbitrary[Version] = Arbitrary(genVersion)
  implicit val arbPath: Arbitrary[Path] = Arbitrary(genPath)

  /** A system-dependent file path. */
  def genPathBelow(root: Path): Gen[Path] = for {
    segmentCount <- Gen.choose(0, 10)
    segments <- Gen.listOfN(segmentCount, Gen.identifier)
  } yield {
    val sub = Paths.get(segments.mkString("/"))
    root.resolve(sub)
  }

  def genVersion: Gen[Version] = for {
    n <- Gen.chooseNum(0,4)
    v <- Gen.listOfN(n, Gen.posNum[Int])
    s <- Gen.identifier.optional
  } yield {
    val dotted = v.mkString(".")
    val suffix = s.map("-" + _).getOrElse("")
    Version(dotted + suffix)
  }

  def genSourceDirectory(root: Path): Gen[SourceDirectory] = for {
    path <- genPathBelow(root)
    generated <- arbitrary[Boolean]
  } yield SourceDirectory(path.toFile, generated)

  def genScalaBuildTargetWithoutTags(withoutTags: List[String]): Gen[BuildTarget] = for {
    target <- genBuildTargetWithScala
    baseDir <- genFileUriString
  } yield {
    val newTags = target.getTags.asScala.filterNot(withoutTags.contains)
    target.setTags(newTags.asJava)
    if (target.getBaseDirectory == null)
      target.setBaseDirectory(baseDir.toString)
    target
  }

  def genScalaSdkData: Gen[ScalaSdkData] = for {
    scalaOrganization <- arbitrary[String]
    scalaVersion <- arbitrary[Option[Version]]
    scalacClasspath <- arbitrary[File].list
    scalacOptions <- arbitrary[String].list
  } yield {
    ScalaSdkData(scalaOrganization, scalaVersion, scalacClasspath, scalacOptions)
  }

  def genModuleKind: Gen[ModuleKind] = for {
    scalaSdkData <- genScalaSdkData
  } yield ScalaModule(scalaSdkData)

  def genModuleDescription: Gen[ModuleDescription] = for {
    targets <- arbitrary[List[BuildTarget]]
    targetDependencies <- arbitrary[Seq[BuildTargetIdentifier]]
    targetTestDependencies <- arbitrary[Seq[BuildTargetIdentifier]]
    basePath <- arbitrary[Path]
    output <- arbitrary[Option[File]]
    testOutput <- arbitrary[Option[File]]
    sourceDirs <- Gen.listOf(genSourceDirectory(basePath))
    testSourceDirs <- Gen.listOf(genSourceDirectory(basePath))
    classPath <- arbitrary[Seq[File]]
    classPathSources <- arbitrary[Seq[File]]
    testClassPath <- arbitrary[Seq[File]]
    testClassPathSources <- arbitrary[Seq[File]]
    moduleKind <- genModuleKind
  } yield {
    val data = ModuleDescriptionData(targets, targetDependencies, targetTestDependencies, basePath.toFile, output, testOutput,
      sourceDirs, testSourceDirs, classPath, classPathSources, testClassPath, testClassPathSources)
    ModuleDescription(data, moduleKind)
  }

}
