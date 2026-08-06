import sbt.complete.DefaultParsers.spaceDelimited

scalaVersion := "2.13.18"

val recordRunConfigurationCommand = inputKey[Unit]("Records parsed run-configuration command arguments")
val failRunConfigurationCommand = taskKey[Unit]("Fails a run-configuration command")

recordRunConfigurationCommand := {
  val args = spaceDelimited("<arg>").parsed
  val outputFile = target.value / "run-configuration-commands.txt"
  IO.createDirectory(outputFile.getParentFile)
  IO.append(outputFile, args.mkString("\n") + "\n---\n")
}

failRunConfigurationCommand := {
  sys.error("run configuration command failed")
}
