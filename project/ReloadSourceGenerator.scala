import sbt.{Def, *}
import sbt.plugins.JvmPlugin

object ReloadSourceGenerator extends AutoPlugin {

  override def requires = JvmPlugin
  override def trigger = allRequirements

  val generateSources = taskKey[Seq[File]]("run all sourceGenerators in current project")
  val generateAllSources = taskKey[Unit]("run all sourceGenerators in ALL project")

  override def projectSettings: Seq[Def.Setting[?]] = Seq(
    generateSources := Def.taskDyn {
      val gens: Seq[Task[Seq[File]]] = (Compile / Keys.sourceGenerators).value
      Def.task {joinTasks(gens).join.value.flatten}
    }.value
  )

  override def buildSettings: Seq[Def.Setting[?]] = Seq(
    generateAllSources := generateSources.all(ScopeFilter(inAnyProject)).value
  )

  override def globalSettings: Seq[Def.Setting[?]] = Seq(
    Keys.onLoad := ((s: State) => { generateAllSources.key.toString :: s}) compose (Global / Keys.onLoad).value
  )

}
