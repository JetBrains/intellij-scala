package org.jetbrains.bsp.project.resolver

import java.io.File
import java.nio.file.Path

import ch.epfl.scala.bsp.gen.Bsp4jArbitrary._
import ch.epfl.scala.bsp.gen.Bsp4jGenerators._
import ch.epfl.scala.bsp.gen.UtilGenerators._
import ch.epfl.scala.bsp4j._
import com.google.gson.{Gson, GsonBuilder}
import com.intellij.openapi.util.io.FileUtil
import org.jetbrains.bsp.project.resolver.BspResolverDescriptors.ScalaModule
import org.jetbrains.bsp.project.resolver.BspResolverLogic._
import org.scalacheck.Prop.{BooleanOperators, forAll}
import org.scalacheck.Shrink.shrink
import org.scalacheck._

import scala.collection.JavaConverters._

object BspResolverLogicProperties extends Properties("BspResolverLogic functions") {

  implicit val gson: Gson = new GsonBuilder().setPrettyPrinting().create()
  implicit val arbFile: Arbitrary[File] = Arbitrary(genPath.map(_.toFile))

  implicit def shrinkJavaList[T](implicit s: Shrink[List[T]]): Shrink[java.util.List[T]] = Shrink { list =>
    for {
      shrunk <- shrink(list.asScala)
    } yield shrunk.asJava
  }

  def genScalaBuildTarget(withoutTags: List[String]): Gen[BuildTarget] = for {
    target <- genBuildTargetWithScala
  } yield {
    val newTags = target.getTags.asScala.filterNot(withoutTags.contains)
    target.setTags(newTags.asJava)
    target
  }

  property("commonBase") = forAll(Gen.listOf(genPath)) { paths: List[Path] =>
    val files = paths.map(_.toFile)
    val base = commonBase(files)
    val findsBase =
      files.nonEmpty ==> base.isDefined
    val baseIsAncestor =
      (files.size > 1) ==> files.forall { f => FileUtil.isAncestor(base.get, f, false) }

    findsBase && baseIsAncestor
  }

  property("getScalaSdkData succeeds") = forAll { (scalaBuildTarget: ScalaBuildTarget, scalacOptionsItem: ScalacOptionsItem) =>

    val data = getScalaSdkData(scalaBuildTarget, Some(scalacOptionsItem))
    val jarsToClasspath = ! scalaBuildTarget.getJars.isEmpty ==> ! data.scalacClasspath.isEmpty

    jarsToClasspath && data.scalaVersion.isDefined
  }

  property("calculateModuleDescriptions succeeds for build targets with Scala") =
    forAll(genScalaBuildTarget(List(BuildTargetTag.NO_IDE)).list) { buildTargets: java.util.List[BuildTarget] =>
      forAll { (optionsItems: List[ScalacOptionsItem], sourcesItems: List[SourcesItem], dependencySourcesItems: List[DependencySourcesItem]) =>
        val targets = buildTargets.asScala
        val descriptions = calculateModuleDescriptions(targets, optionsItems, sourcesItems, dependencySourcesItems)
        (targets.nonEmpty && targets.exists(_.getBaseDirectory != null)) ==> descriptions.nonEmpty
      }
    }

  property("moduleDescriptionForTarget succeeds for build targets with Scala") =
    forAll(genBuildTargetWithScala) { target: BuildTarget =>
      forAll { (scalacOptions: Option[ScalacOptionsItem], depSourcesOpt: Option[DependencySourcesItem], sourcesOpt: Option[SourcesItem], dependencyOutputs: List[File]) =>
        val description = moduleDescriptionForTarget(target, scalacOptions, depSourcesOpt, sourcesOpt, dependencyOutputs)
        val emptyForNOIDE = target.getTags.contains(BuildTargetTag.NO_IDE) ==> description.isEmpty :| "contained NO_IDE tag, but created anyway"
        val definedForBaseDir = target.getBaseDirectory != null ==> description.isDefined :| "base dir defined, but not created"
        val hasScalaModule = description.isDefined ==> description.get.moduleKindData.isInstanceOf[ScalaModule]
        emptyForNOIDE || (definedForBaseDir && hasScalaModule)
      }
    }

}
