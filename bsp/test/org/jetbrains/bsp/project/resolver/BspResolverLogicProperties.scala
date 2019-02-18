package org.jetbrains.bsp.project.resolver

import java.io.File
import java.nio.file.Path

import ch.epfl.scala.bsp.testkit.gen.Bsp4jGenerators._
import ch.epfl.scala.bsp.testkit.gen.bsp4jArbitrary._
import ch.epfl.scala.bsp4j._
import com.google.gson.{Gson, GsonBuilder}
import com.intellij.openapi.externalSystem.model.ProjectKeys
import com.intellij.openapi.util.io.FileUtil
import org.jetbrains.bsp.project.resolver.BspResolverDescriptors.{ModuleDescription, ScalaModule, SourceDirectory}
import org.jetbrains.bsp.project.resolver.BspResolverLogic._
import org.jetbrains.bsp.project.resolver.Generators._
import org.scalacheck.Prop.{BooleanOperators, forAll}
import org.scalacheck._

import scala.collection.JavaConverters._

object BspResolverLogicProperties extends Properties("BspResolverLogic functions") {

  implicit val gson: Gson = new GsonBuilder().setPrettyPrinting().create()

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
    forAll(Gen.listOf(genScalaBuildTargetWithoutTags(List(BuildTargetTag.NO_IDE)))) { buildTargets: List[BuildTarget] =>
      forAll { (optionsItems: List[ScalacOptionsItem], sourcesItems: List[SourcesItem], dependencySourcesItems: List[DependencySourcesItem]) =>
        val descriptions = calculateModuleDescriptions(buildTargets, optionsItems, sourcesItems, dependencySourcesItems)
        (buildTargets.nonEmpty && buildTargets.exists(_.getBaseDirectory != null)) ==> descriptions.nonEmpty
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

  property("createScalaModuleDescription") =
    forAll(genPath, Gen.listOf(genBuildTargetTag)) { (basePath: Path, tags: List[String]) =>
      forAll(Gen.listOf(genSourceDirectory(basePath))) { sourceRoots: List[SourceDirectory] =>
        forAll { (target: BuildTarget, moduleBase: File, outputPath: Option[File], classpath: List[File], dependencySources: List[File]) =>
          val description = createScalaModuleDescription(target, tags, moduleBase, outputPath, sourceRoots, classpath, dependencySources)

          val p1 = (description.basePath == moduleBase) :| "base path should be set"
          val p2 = (tags.contains(BuildTargetTag.LIBRARY) || tags.contains(BuildTargetTag.APPLICATION)) ==>
            (description.output == outputPath &&
              description.targetDependencies == target.getDependencies.asScala &&
              description.classpathSources == dependencySources &&
              description.sourceDirs == sourceRoots &&
              description.classpath == classpath) :|
              s"data not correctly set for library or application tags. Result data was: $description"
          val p3 = tags.contains(BuildTargetTag.TEST) ==>
            (description.testOutput == outputPath &&
              description.targetTestDependencies == target.getDependencies.asScala &&
              description.testClasspathSources == dependencySources &&
              description.testSourceDirs == sourceRoots &&
              description.testClasspath == classpath) :|
              s"data not correctly set for test tag. Result data was: $description"

          p1 && p2 && p3
        }
      }
    }

  property("mergeModules succeeds") = forAll { (description1: ModuleDescription, description2: ModuleDescription) =>
    val data1 = description1.data
    val data2 = description2.data
    val merged = mergeModules(List(description1, description2))
    val data = merged.data

    // TODO more thorough properties
    data.basePath == data1.basePath &&
      data.targets == (data1.targets ++ data2.targets).sortBy(_.getId.getUri)
  }

  property("projectNode succeeds") = forAll {
    (root: Path, moduleFilesDir: Path, moduleDescriptions: List[ModuleDescription]) =>

    val projectRootPath = root.toString
    val node = projectNode(projectRootPath, moduleFilesDir.toString, moduleDescriptions)

    node.getChildren.size >= moduleDescriptions.size
      node.getChildren.asScala.exists { node =>
        node.getData(ProjectKeys.MODULE).getLinkedExternalProjectPath == projectRootPath
      }
  }

}
