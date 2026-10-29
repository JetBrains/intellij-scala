package org.jetbrains.sbt.project

import com.intellij.openapi.roots.DependencyScope
import org.jetbrains.sbt.SbtTestDataUtils
import org.jetbrains.sbt.project.ProjectStructureDsl.*

/** Fixtures shared by the sbt 2 matrix highlighting and importing/build tests (SCL-25328). */
object SbtProjectMatrixTestUtils {
  final case class Platform(sourceSuffix: String, idSuffix: String, outputDirectory: String)

  val Jvm: Platform = Platform("jvm", "", "jvm")
  val Js: Platform = Platform("js", "JS", "sjs1")
  val Native: Platform = Platform("native", "Native", "native0.5")

  final case class Fixture(
    projectName: String,
    platforms: Seq[Platform],
    scalaVersions: Seq[String] = Seq("2.12.21", "2.13.18"),
    matrixNames: Seq[String] = Seq("upstream", "downstream"),
    fullCrossVersion: Boolean = false,
  ) {
    def testDataPath: String = SbtTestDataUtils.resolveRelativePath(
      s"sbt-project-highlighting-tests/testdata/projectsForHighlightingTests/local/$projectName"
    )
  }

  val JvmAndJs: Fixture = Fixture("sbt-projectmatrix-sbt2-jvm-js", Seq(Jvm, Js))
  val JsAndNative: Fixture = Fixture("sbt-projectmatrix-sbt2-js-native", Seq(Js, Native))
  val JvmAndJsAndNative: Fixture = Fixture("sbt-projectmatrix-sbt2-jvm-js-native", Seq(Jvm, Js, Native))
  val CrossVersionFull: Fixture = Fixture(
    "sbt-projectmatrix-sbt2-cross-version-full",
    Seq(Jvm),
    scalaVersions = Seq("2.13.10", "2.13.18"),
    matrixNames = Seq("foo", "reordered"),
    fullCrossVersion = true,
  )

  private def binaryVersion(version: String): String = version.split('.').take(2).mkString(".")

  private def emptyModule(moduleName: String, group: Array[String] = Array.empty): module =
    new module(moduleName, group) {
      contentRoots := Nil
      sources := Nil
      testSources := Nil
      excluded := Nil
      moduleDependencies := Nil
    }

  private def setSources(m: module, scope: String, roots: Seq[String]): Unit = {
    import m.*
    if (scope == "main") sources := roots
    else testSources := roots
  }

  private def setResources(m: module, scope: String, roots: Seq[String]): Unit = {
    import m.*
    if (scope == "main") resources := roots
    else testResources := roots
  }

  private def compileDependency(m: module): dependency[module] =
    new dependency(m) {
      scope := DependencyScope.COMPILE
    }

  private def managedRoots(platform: Platform, version: String, matrixName: String, scope: String): Seq[String] = {
    val output = s"%PROJECT_ROOT%/target/out/${platform.outputDirectory}/scala-$version/$matrixName"
    Seq(s"$output/src_managed/$scope", s"$output/resource_managed/$scope")
  }

  /** Keep the model explicit: no expectations are derived from the imported project. */
  def expectedProject(fixture: Fixture): project = {
    val projectName = fixture.projectName
    val root = emptyModule(projectName)
    root.defineAttribute(contentRoots) := Seq("%PROJECT_ROOT%")
    root.defineAttribute(excluded) := Seq("target")
    val rootMain = emptyModule(s"$projectName.main")
    val rootTest = emptyModule(s"$projectName.test")
    Seq("main" -> rootMain, "test" -> rootTest).foreach { case (scope, m) =>
      val managed = managedRoots(Jvm, "2.12.17", projectName, scope)
      m.defineAttribute(contentRoots) := Seq(s"%PROJECT_ROOT%/src/$scope") ++ managed
      val unmanaged = Seq("java", "scala", "scala-2", "scala-2.12").map(s"%PROJECT_ROOT%/src/$scope/" + _)
      setSources(m, scope, unmanaged ++ managed.take(1))
      setResources(m, scope, Seq(s"%PROJECT_ROOT%/src/$scope/resources") ++ managed.drop(1))
    }
    root.dependsOn(rootMain, rootTest)
    rootTest.dependsOn(rootMain)
    val build = emptyModule(s"$projectName.$projectName-build")
    build.defineAttribute(contentRoots) := Seq("%PROJECT_ROOT%/project")
    build.defineAttribute(sources) := Seq("%PROJECT_ROOT%/project")
    build.defineAttribute(excluded) := Seq("target", "project/target")

    final case class Row(platform: Platform, version: String, parent: module, main: module, test: module)
    final case class Matrix(parent: module, main: module, test: module, rows: Seq[Row]) {
      def modules: Seq[module] = Seq(parent, main, test) ++ rows.flatMap(r => Seq(r.parent, r.main, r.test))
    }

    val matrices = fixture.matrixNames.map { matrixName =>
      val group = Array(projectName, matrixName)
      val shared = emptyModule(s"$matrixName-sources", group)
      shared.defineAttribute(contentRoots) := Seq(s"%PROJECT_ROOT%/$matrixName")
      shared.defineAttribute(excluded) := Seq("target")
      val sharedMain = emptyModule(s"$matrixName-sources.main", group)
      val sharedTest = emptyModule(s"$matrixName-sources.test", group)
      Seq("main" -> sharedMain, "test" -> sharedTest).foreach { case (scope, m) =>
        val base = s"%PROJECT_ROOT%/$matrixName/src/$scope"
        m.defineAttribute(contentRoots) := Seq(base)
        val platformRoots = fixture.platforms.flatMap(p => Seq(s"java${p.sourceSuffix}", s"scala${p.sourceSuffix}"))
        val scalaRoots = fixture.scalaVersions.map(binaryVersion).distinct.map("scala-" + _)
        setSources(m, scope, (Seq("java", "scala", "scala-2") ++ scalaRoots ++ platformRoots).map(base + "/" + _))
      }
      shared.dependsOn(sharedMain, sharedTest)

      val rows = for {
        platform <- fixture.platforms
        version <- fixture.scalaVersions
      } yield {
        val axisVersion = if (fixture.fullCrossVersion) version else binaryVersion(version)
        val id = matrixName + platform.idSuffix + axisVersion.replace('.', '_')
        val parent = emptyModule(id, group)
        parent.defineAttribute(contentRoots) := Seq(s"%PROJECT_ROOT%/.sbt/matrix/$id")
        parent.defineAttribute(excluded) := Seq("target")
        parent.defineAttribute(sbtProjectId) := id
        val main = emptyModule(s"$id.main", group)
        val test = emptyModule(s"$id.test", group)
        Seq("main" -> main, "test" -> test).foreach { case (scope, m) =>
          val platformRoot = s"%PROJECT_ROOT%/$matrixName/src/$scope/scala${platform.sourceSuffix}-$axisVersion"
          val managed = managedRoots(platform, version, matrixName, scope)
          m.defineAttribute(contentRoots) := Seq(platformRoot) ++ managed
          setSources(m, scope, Seq(platformRoot) ++ managed.take(1))
          setResources(m, scope, managed.drop(1))
        }
        parent.dependsOn(main, test)
        // Shared sources are COMPILE dependencies even in the separate test modules.
        main.dependsOn(compileDependency(sharedMain))
        test.dependsOn(compileDependency(sharedMain), compileDependency(sharedTest), main)
        Row(platform, version, parent, main, test)
      }
      // Match the importer's deterministic representative choice, including the JS+Native case without a JVM row.
      val representative = rows.minBy(_.parent.moduleName)
      sharedTest.dependsOn(representative.main)
      matrixName -> Matrix(shared, sharedMain, sharedTest, rows)
    }.toMap

    if (matrices.contains("downstream")) {
      val upstream = matrices("upstream")
      val downstream = matrices("downstream")
      val representativeUpstream = upstream.rows.minBy(_.parent.moduleName)
      val representativeDownstream = downstream.rows.minBy(_.parent.moduleName)
      downstream.main.dependsOn(representativeUpstream.main)
      downstream.test.dependsOn(representativeDownstream.main, representativeUpstream.main)
      downstream.rows.foreach { row =>
        val matchingUpstream = upstream.rows.find(r => r.platform == row.platform && r.version == row.version).get
        row.main.dependsOn(compileDependency(downstream.main), compileDependency(upstream.main), matchingUpstream.main)
        row.test.dependsOn(
          compileDependency(downstream.main),
          compileDependency(downstream.test),
          row.main,
          compileDependency(upstream.main),
          matchingUpstream.main
        )
      }
    }

    new project(projectName) {
      modules := Seq(root, rootMain, rootTest, build) ++ fixture.matrixNames.flatMap(matrices(_).modules)
    }
  }
}
