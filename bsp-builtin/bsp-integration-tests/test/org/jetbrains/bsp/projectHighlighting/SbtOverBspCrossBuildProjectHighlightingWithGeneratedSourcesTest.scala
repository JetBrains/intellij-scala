package org.jetbrains.bsp.projectHighlighting

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import org.jetbrains.plugins.scala.projectHighlighting.reporter.HighlightingProgressReporter
import org.jetbrains.sbt.project.ProjectStructureDsl.{contentRoots, module}
import org.jetbrains.sbt.project.utils.ProjectStructureComparisonContext
import org.jetbrains.sbt.project.{ProjectStructureDsl, ProjectStructureMatcher}

class SbtOverBspCrossBuildProjectHighlightingWithGeneratedSourcesTest extends SbtOverBspProjectHighlightingLocalProjectsTestBase {

  override def projectName = "sbt-crossproject-test-project-with-generated-sources"

  override protected def highlightSingleFile(
    virtualFile: VirtualFile,
    psiFile: PsiFile,
    reporter: HighlightingProgressReporter,
  ): Unit =
    doHighlightingForFile(virtualFile, psiFile, reporter)

  private def standardRootsForPureSharedModuleJSJVM(m: module, moduleBaseName: String, scope: String): Unit = {
    import m._
    contentRoots := Seq(
      s"%PROJECT_ROOT%/$moduleBaseName/src/$scope/resources",
      s"%PROJECT_ROOT%/$moduleBaseName/src/$scope/scala-2.13",
      s"%PROJECT_ROOT%/$moduleBaseName/src/$scope/scala",
      s"%PROJECT_ROOT%/$moduleBaseName/src/$scope/scala-2",
      s"%PROJECT_ROOT%/$moduleBaseName/.jvm/target/scala-2.13/src_managed/$scope",
      s"%PROJECT_ROOT%/$moduleBaseName/.js/target/scala-2.13/src_managed/$scope"
    )

    if (scope == "test") {
      contentRoots ++= Seq(
        s"%PROJECT_ROOT%/$moduleBaseName/.js/src/$scope/resources",
        s"%PROJECT_ROOT%/$moduleBaseName/.jvm/target/scala-2.13/resource_managed/$scope",
        s"%PROJECT_ROOT%/$moduleBaseName/.js/target/scala-2.13/resource_managed/$scope",
        s"%PROJECT_ROOT%/$moduleBaseName/.jvm/src/$scope/resources",
      )
    } else
      ProjectStructureDsl.sources := Seq(
        s"%PROJECT_ROOT%/$moduleBaseName/.js/target/scala-2.13/src_managed/$scope",
        s"%PROJECT_ROOT%/$moduleBaseName/.jvm/target/scala-2.13/src_managed/$scope",
        s"%PROJECT_ROOT%/$moduleBaseName/src/$scope/scala"
      )
  }

  private def standardRootsForFullSharedModuleJSJVM(m: module, moduleBaseName: String, scope: String): Unit = {
    import m._
    contentRoots := Seq(
      s"%PROJECT_ROOT%/$moduleBaseName/shared/src/$scope/resources",
      s"%PROJECT_ROOT%/$moduleBaseName/shared/src/$scope/scala-2.13",
      s"%PROJECT_ROOT%/$moduleBaseName/shared/src/$scope/scala",
      s"%PROJECT_ROOT%/$moduleBaseName/shared/src/$scope/scala-2",
      s"%PROJECT_ROOT%/$moduleBaseName/jvm/target/scala-2.13/src_managed/$scope",
      s"%PROJECT_ROOT%/$moduleBaseName/js/target/scala-2.13/src_managed/$scope"
    )

    if (scope == "test") {
      contentRoots ++= Seq(
        s"%PROJECT_ROOT%/$moduleBaseName/js/src/$scope/resources",
        s"%PROJECT_ROOT%/$moduleBaseName/jvm/target/scala-2.13/resource_managed/$scope",
        s"%PROJECT_ROOT%/$moduleBaseName/js/target/scala-2.13/resource_managed/$scope",
        s"%PROJECT_ROOT%/$moduleBaseName/jvm/src/$scope/resources",
      )
    } else
      ProjectStructureDsl.sources := Seq(
        s"%PROJECT_ROOT%/$moduleBaseName/js/target/scala-2.13/src_managed/$scope",
        s"%PROJECT_ROOT%/$moduleBaseName/jvm/target/scala-2.13/src_managed/$scope",
        s"%PROJECT_ROOT%/$moduleBaseName/shared/src/$scope/scala"
      )
  }

  private def standardRootsForPureModule(m: module, moduleBaseName: String, platform: String): Unit = {
    import ProjectStructureDsl._
    import m._
    contentRoots += s"%PROJECT_ROOT%/$moduleBaseName/.$platform"
    excluded += "target"
  }

  private def standardRootsForFullModule(m: module, moduleBaseName: String, platform: String): Unit = {
    import ProjectStructureDsl._
    import m._
    contentRoots += s"%PROJECT_ROOT%/$moduleBaseName/$platform"
    excluded += "target"
  }

  private class myModule(
    name: String,
    group: Array[String] = Array.empty,
  ) extends module(name, group) {
    import ProjectStructureDsl._
    locally {
      contentRoots := Seq()
      sources := Seq()
      testSources := Seq()
      moduleDependencies := Seq()
      excluded := Seq()
      // NOTE: we don't test resources directories as they should behave similar to sources/testSources
      // We comment them out to avoid too much test data.
      // We could bring it back once it becomes essential for some use cases
      //resources := Seq()
      //testResources := Seq()
    }
  }

  //noinspection ScalaUnusedSymbol,TypeAnnotation
  def testProjectStructure(): Unit = {
    import org.jetbrains.sbt.project.ProjectStructureDsl._

    importProject(false)

    val expectedProject: project = new project(projectName) {
      val `sbt-crossproject-test-project-with-generated-sources` = new myModule("sbt-crossproject-test-project-with-generated-sources"){
        excluded := Seq(".bsp", ".bloop", "target")
        contentRoots := Seq("%PROJECT_ROOT%")
      }
      val `sbt-crossproject-test-project-with-generated-sources-build` = new myModule("sbt-crossproject-test-project-with-generated-sources-build") {
        contentRoots := Seq("%PROJECT_ROOT%/project")
      }

      val `downstreamPure-sources` = new myModule("downstreamPure(JS+JVM) (shared)") {
        standardRootsForPureSharedModuleJSJVM(this, "downstreamPure", "main")
      }
      val `downstreamPure-test-sources` = new myModule("downstreamPure(JS+JVM)-test (shared)") {
        standardRootsForPureSharedModuleJSJVM(this, "downstreamPure", "test")
      }

      val `downstreamPureJS` = new myModule("downstreamPureJS") {
        standardRootsForPureModule(this, "downstreamPure", "js")
      }
      val `downstreamPureJVM` = new myModule("downstreamPureJVM") {
        standardRootsForPureModule(this, "downstreamPure", "jvm")
      }

      val `upstreamPure-sources` = new myModule("upstreamPure(JS+JVM) (shared)") {
        standardRootsForPureSharedModuleJSJVM(this, "upstreamPure", "main")
      }
      val `upstreamPure-test-sources` = new myModule("upstreamPure(JS+JVM)-test (shared)") {
        standardRootsForPureSharedModuleJSJVM(this, "upstreamPure", "test")
      }

      val `upstreamPureJS` = new myModule("upstreamPureJS") {
        standardRootsForPureModule(this, "upstreamPure", "js")
      }
      val `upstreamPureJVM` = new myModule("upstreamPureJVM") {
        standardRootsForPureModule(this, "upstreamPure", "jvm")
      }

      val `downstreamFull-sources` = new myModule("downstreamFull(JS+JVM) (shared)") {
        standardRootsForFullSharedModuleJSJVM(this, "downstreamFull", "main")
      }
      val `downstreamFull-test-sources` = new myModule("downstreamFull(JS+JVM)-test (shared)") {
        standardRootsForFullSharedModuleJSJVM(this, "downstreamFull", "test")
      }

      val `downstreamFullJS` = new myModule("downstreamFullJS") {
        standardRootsForFullModule(this, "downstreamFull", "js")
      }
      val `downstreamFullJVM` = new myModule("downstreamFullJVM") {
        standardRootsForFullModule(this, "downstreamFull", "jvm")
      }

      val `upstreamFull-sources` = new myModule("upstreamFull(JS+JVM) (shared)") {
        standardRootsForFullSharedModuleJSJVM(this, "upstreamFull", "main")
      }
      val `upstreamFull-test-sources` = new myModule("upstreamFull(JS+JVM)-test (shared)") {
        standardRootsForFullSharedModuleJSJVM(this, "upstreamFull", "test")
      }

      val `upstreamFullJS` = new myModule("upstreamFullJS") {
        standardRootsForFullModule(this, "upstreamFull", "js")
      }
      val `upstreamFullJVM` = new myModule("upstreamFullJVM") {
        standardRootsForFullModule(this, "upstreamFull", "jvm")
      }

      //
      // Define module groups separately for better test data readability
      //
      val downstreamPureGroup: Seq[myModule] = Seq(
        `downstreamPure-sources`,
        `downstreamPure-test-sources`,
        `downstreamPureJS`,
        `downstreamPureJVM`,
      )
      val upstreamPureGroup: Seq[myModule] = Seq(
        `upstreamPure-sources`,
        `upstreamPure-test-sources`,
        `upstreamPureJS`,
        `upstreamPureJVM`,
      )
      val downstreamFullGroup: Seq[myModule] = Seq(
        `downstreamFull-sources`,
        `downstreamFull-test-sources`,
        `downstreamFullJS`,
        `downstreamFullJVM`,
      )
      val upstreamFullGroup: Seq[myModule] = Seq(
        `upstreamFull-sources`,
        `upstreamFull-test-sources`,
        `upstreamFullJS`,
        `upstreamFullJVM`,
      )

      //
      // Define dependencies between modules separately for better test data readability
      //
      `downstreamPure-sources`.dependsOn(`upstreamPureJVM`, `upstreamPureJVM`, `upstreamPureJS`, `upstreamPureJS`)
      `downstreamPureJVM`.dependsOn(`downstreamPure-test-sources`, `downstreamPure-sources`, `upstreamPureJVM`, `upstreamPureJVM`)
      `downstreamPureJS`.dependsOn(`downstreamPure-test-sources`, `downstreamPure-sources`, `upstreamPureJS`, `upstreamPureJS`)

      `upstreamPure-sources`.dependsOn()
      `upstreamPureJVM`.dependsOn(`upstreamPure-sources`, `upstreamPure-test-sources`)
      `upstreamPureJS`.dependsOn(`upstreamPure-sources`, `upstreamPure-test-sources`)

      `downstreamFull-sources`.dependsOn(`upstreamFullJVM`, `upstreamFullJVM`, `upstreamFullJS`, `upstreamFullJS`)
      `downstreamFullJVM`.dependsOn(`upstreamFullJVM`, `downstreamFull-sources`, `downstreamFull-test-sources`, `upstreamFullJVM`)
      `downstreamFullJS`.dependsOn(`downstreamFull-test-sources`, `downstreamFull-sources`, `upstreamFullJS`, `upstreamFullJS`)

      `upstreamFull-sources`.dependsOn()
      `upstreamFullJVM`.dependsOn(`upstreamFull-sources`, `upstreamFull-test-sources`)
      `upstreamFullJS`.dependsOn(`upstreamFull-sources`, `upstreamFull-test-sources`)

      modules := Seq(
        `sbt-crossproject-test-project-with-generated-sources`,
        `sbt-crossproject-test-project-with-generated-sources-build`,
      ) ++
        downstreamPureGroup ++
        upstreamPureGroup ++
        downstreamFullGroup ++
        upstreamFullGroup
    }

    val matcher = new ProjectStructureMatcher {
      override protected def defaultAssertMatch: ProjectStructureMatcher.AttributeMatchType =
        ProjectStructureMatcher.AttributeMatchType.Exact
    }
    val compareContext = ProjectStructureComparisonContext.Implicit.default(using getProject)
      .withOptions(_.copy(strictCheckForBuildModules = true))
    matcher.assertProjectsEqual(expectedProject, getProject, singleContentRootModules = false)(using compareContext)
  }
}
