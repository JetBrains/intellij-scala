import sbt._
import sbt.plugins.JvmPlugin

object ReloadSourceGenerator extends AutoPlugin {

  override def requires = JvmPlugin
  override def trigger = allRequirements

  val generateSources = taskKey[Seq[File]]("run all sourceGenerators")

  override def projectSettings: Seq[Def.Setting[_]] = Seq(
    generateSources := Def.taskDyn {
      val gens: Seq[Task[Seq[File]]] = (Keys.sourceGenerators in Compile).value
      Def.task {joinTasks(gens).join.value.flatten}
    }.value
  )

  override def globalSettings: Seq[Def.Setting[_]] = Seq(
    Keys.onLoad := ((s: State) => { generateSources.key.toString :: s}) compose (Keys.onLoad in Global).value
  )

}
