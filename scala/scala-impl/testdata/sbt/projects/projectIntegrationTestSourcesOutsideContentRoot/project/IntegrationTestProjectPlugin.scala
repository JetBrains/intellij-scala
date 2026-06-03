import sbt.Keys.*
import sbt.io.syntax.*
import sbt.{AutoPlugin, LocalProject, Project, ProjectDefinition, ProjectOrigin, Test}

//NOTE: this is a reduced version of example project from SCL-23577
/**
 * Sbt >= 1.9.0 has deprecated `IntegrationTest` as a config favoring a new project instead.
 * This plugin automatically gets applied to every project in the build and creates a new project
 * with a suffix of [[CoatueDefaults.integrationTestProjectSuffix]]. It then looks at the `it`
 * directory in the referenced project to find the actual sources.
 *
 * To keep things tidy, all of these projects place their `target` and other directories under the
 * `derived-projects` directory.
 */
object IntegrationTestProjectPlugin extends AutoPlugin {
  val integrationTestProjectSuffix = "-integration-test"

  override def trigger = allRequirements //Make this show up in all projects

  override def derivedProjects(proj: ProjectDefinition[_]): Seq[Project] =
    if (proj.projectOrigin != ProjectOrigin.DerivedProject && proj.id != "root") {
      Seq(
        Project(
          s"${proj.id}$integrationTestProjectSuffix",
          new File(
            s"${proj.base.getParent}${java.io.File.separator}derived-projects",
            s"${proj.base.getName}$integrationTestProjectSuffix"
          )
        ).settings(
            Test / sourceDirectory := (LocalProject(proj.id) / sourceDirectory).value / "it",
            libraryDependencies := (LocalProject(proj.id) / libraryDependencies).value,
          )
          .dependsOn(
            LocalProject(proj.id) % "compile->compile;test->test"
          )
      )
    }
    else Seq.empty
}

