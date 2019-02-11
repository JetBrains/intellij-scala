package org.jetbrains.bsp.project.resolver

import java.io.File

import ch.epfl.scala.bsp.testkit.gen.Bsp4jGenerators._
import ch.epfl.scala.bsp.testkit.gen.UtilGenerators.{genFileUriString, genPath}
import ch.epfl.scala.bsp4j.BuildTarget
import com.google.gson.{Gson, GsonBuilder}
import org.jetbrains.bsp.project.resolver.BspResolverDescriptors.{ModuleDescription, SourceDirectory}
import org.scalacheck.Arbitrary.arbitrary
import org.scalacheck.{Arbitrary, Gen}

import scala.collection.JavaConverters._

import BspResolverDescriptors._

object Generators {

  implicit val gson: Gson = new GsonBuilder().setPrettyPrinting().create()

  implicit val arbFile: Arbitrary[File] = Arbitrary(genPath.map(_.toFile))

  implicit lazy val arbSourceDirectory: Arbitrary[SourceDirectory] = Arbitrary {
    for {
      file <- arbFile.arbitrary
      generated <- arbitrary[Boolean]
    } yield SourceDirectory(file, generated)
  }

  def genScalaBuildTarget(withoutTags: List[String]): Gen[BuildTarget] = for {
    target <- genBuildTargetWithScala
    baseDir <- genFileUriString
  } yield {
    val newTags = target.getTags.asScala.filterNot(withoutTags.contains)
    target.setTags(newTags.asJava)
    if (target.getBaseDirectory == null)
      target.setBaseDirectory(baseDir.toString)
    target
  }

  def genModuleDescription: Gen[ModuleDescription] = for {
    val data = ModuleDescriptionData()
//    val kind = ModuleKindData()
    ModuleDescription()
  }


}
