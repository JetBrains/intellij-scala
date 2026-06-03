package org.jetbrains.bsp.project.importing

import ch.epfl.scala.bsp.testkit.gen.Bsp4jGenerators.*
import ch.epfl.scala.bsp.testkit.gen.bsp4jArbitrary.*
import ch.epfl.scala.bsp4j.*
import com.google.gson.{Gson, GsonBuilder}
import com.intellij.openapi.externalSystem.model.ProjectKeys
import com.intellij.platform.eel.EelDescriptor
import com.intellij.platform.eel.provider.LocalEelDescriptor
import com.intellij.pom.java.LanguageLevel
import org.jetbrains.bsp.project.importing.BspResolverDescriptors.{ModuleDescription, ModuleKind, ProjectModules, SourceEntry}
import org.jetbrains.bsp.project.importing.BspResolverLogic.*
import org.jetbrains.bsp.project.importing.Generators.*
import org.jetbrains.bsp.project.importing.Generators.given
import org.jetbrains.sbt.project.structure.data.InterpretablePath
import org.jetbrains.plugins.scala.SlowTests
import org.junit.experimental.categories.Category
import org.junit.{Ignore, Test}
import org.scalacheck.Prop.{forAll, propBoolean}
import org.scalacheck.*
import org.scalatestplus.scalacheck.Checkers

import java.nio.file.Path
import scala.jdk.CollectionConverters.*

@Category(Array(classOf[SlowTests]))
class BspResolverLogicProperties extends Checkers {

  private given EelDescriptor = LocalEelDescriptor.INSTANCE

  given Gson = new GsonBuilder().setPrettyPrinting().create()

  given PropertyCheckConfiguration = PropertyCheckConfiguration(sizeRange = 20)

  @Test @Ignore
  def testGetScalaSdkData(): Unit = check(
    forAll { (scalaBuildTarget: ScalaBuildTarget, scalacOptionsItem: ScalacOptionsItem) =>

      val (_, data) = getScalaSdkData(scalaBuildTarget, Some(scalacOptionsItem))
      val jarsToClasspath = ! scalaBuildTarget.getJars.isEmpty ==> ! data.scalacClasspath.isEmpty

      jarsToClasspath && data.scalaVersion != null
    })

  @Test @Ignore
  def `calculateModuleDescriptions succeeds for build targets with Scala`() : Unit = check(
    forAll(Gen.listOf(genScalaBuildTargetWithoutTags(List(BuildTargetTag.NO_IDE)))) { (buildTargets: List[BuildTarget]) =>
      forAll { (scalacOptionsItems: List[ScalacOptionsItem], javacOptionsItems: List[JavacOptionsItem], sourcesItems: List[SourcesItem], resourcesItems: List[ResourcesItem], outputPathsItems: List[OutputPathsItem], dependencySourcesItems: List[DependencySourcesItem]) =>
        val descriptions = calculateModuleDescriptions(buildTargets, scalacOptionsItems, javacOptionsItems, sourcesItems, resourcesItems, outputPathsItems, dependencySourcesItems)
        val moduleIds = (descriptions.modules ++ descriptions.synthetic).map(_.data.idUri)
        val moduleForEveryTarget = (buildTargets.nonEmpty && buildTargets.exists(_.getBaseDirectory != null)) ==> descriptions.modules.nonEmpty
        val noDuplicateIds = moduleIds.size == moduleIds.distinct.size // TODO generator needs to create shared source dirs
        moduleForEveryTarget
      }
    }
  )

  @Test @Ignore
  def `test moduleDescriptionForTarget succeeds for build targets with Scala`(): Unit = check(
    forAll(genBuildTargetWithScala) { (target: BuildTarget) =>
      forAll { (scalacOptions: Option[ScalacOptionsItem], javacOptions: Option[JavacOptionsItem], depSources: Seq[Path], sources: Seq[SourceEntry], resources: Seq[SourceEntry], outputPaths: Seq[Path], dependencyOutputs: List[Path]) =>

        val description = moduleDescriptionForTarget(target, scalacOptions, javacOptions, depSources.map(InterpretablePath.construct), sources, resources, outputPaths.map(InterpretablePath.construct(_)), dependencyOutputs.map(InterpretablePath.construct(_)))
        val emptyForNOIDE = target.getTags.contains(BuildTargetTag.NO_IDE) ==> description.isEmpty :| "contained NO_IDE tag, but created anyway"
        val definedForBaseDir = target.getBaseDirectory != null ==> description.isDefined :| "base dir defined, but not created"
        val hasScalaModule = description.isDefined ==> description.get.moduleKindData.isInstanceOf[ModuleKind.ScalaModule]
        emptyForNOIDE || (definedForBaseDir && hasScalaModule)
      }
    }
  )

  @Test @Ignore
  def `test createScalaModuleDescription`(): Unit = check(
    forAll(genPath, Gen.listOf(genBuildTargetTag)) { (basePath: Path, tags: List[String]) =>
      forAll(Gen.listOf(genSourceDirectoryUnder(basePath)), Gen.listOf(genSourceDirectoryUnder(basePath))) {
        (sourceRoots: List[SourceEntry], resourceRoots: List[SourceEntry]) =>
          forAll { (target: BuildTarget, moduleBase: Option[Path], outputPath: Option[Path], classpath: List[Path], dependencySources: List[Path], outputPaths: List[Path], languageLevel: LanguageLevel) =>
            val moduleBaseIP = moduleBase.map(InterpretablePath.construct)
            val outputPathIP = outputPath.map(InterpretablePath.construct)
            val classpathIP = classpath.map(InterpretablePath.construct)
            val dependencySourcesIP = dependencySources.map(InterpretablePath.construct)
            val outputPathsIP = outputPaths.map(InterpretablePath.construct)
            val description = createModuleDescriptionData(target, tags, moduleBaseIP, outputPathIP, sourceRoots, resourceRoots, outputPathsIP, classpathIP, dependencySourcesIP, Some(languageLevel))

            val p1 = (description.basePath == moduleBaseIP) :| "base path should be set"
            val p2 = (tags.contains(BuildTargetTag.LIBRARY) || tags.contains(BuildTargetTag
              .APPLICATION)) ==>
              (description.output == outputPathIP &&
                description.targetDependencies == target.getDependencies.asScala &&
                description.classpathSources == dependencySourcesIP &&
                description.sourceRoots == sourceRoots &&
                description.outputPaths == outputPathsIP &&
                description.classpath == classpathIP) :|
                s"data not correctly set for library or application tags. Result data was: $description"
            val p3 = tags.contains(BuildTargetTag.TEST) ==>
              (description.testOutput == outputPathIP &&
                description.targetTestDependencies == target.getDependencies.asScala &&
                description.testClasspathSources == dependencySourcesIP &&
                description.testSourceRoots == sourceRoots &&
                description.testClasspath == classpathIP) :|
                s"data not correctly set for test tag. Result data was: $description"

            p1 && p2 && p3
          }
      }
    }
)

  @Test @Ignore
  def `test mergeModules`(): Unit = check(
    forAll { (description1: ModuleDescription, description2: ModuleDescription) =>
      val data1 = description1.data
      val data2 = description2.data
      val merged = mergeModules(List(description1, description2))
      val data = merged.data

      // TODO more thorough properties
      data.basePath == data1.basePath &&
        data.targets == (data1.targets ++ data2.targets).sortBy(_.getId.getUri)
    }
  )

  @Test @Ignore
  def `test projectNode`(): Unit = check(
    forAll {
      (root: Path, moduleDescriptions: List[ModuleDescription]) =>

        val projectRootPath = root.toString
        val projectModules = ProjectModules(moduleDescriptions, Seq.empty)
        val node = projectNode(root, projectModules, BspProjectResolver.rootExclusions(root), "displayName", List.empty)

        // TODO more thorough properties
        node.getChildren.size >= moduleDescriptions.size
        node.getChildren.asScala.exists { node =>
          node.getData(ProjectKeys.MODULE).getLinkedExternalProjectPath == projectRootPath
        }
    }
  )
}
