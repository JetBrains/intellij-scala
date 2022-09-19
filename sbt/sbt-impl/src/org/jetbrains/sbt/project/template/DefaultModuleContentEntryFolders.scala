package org.jetbrains.sbt.project.template

/**
 * Represents set of folders to be marked as "source", "resource", "excluded" folders just after project is created.
 * These values will be rewritten after sbt project will be reimported (it will be read from sbt project dumped structure).
 * So this class contains a "guess" set of folders.
 * (Luckily it's quite well known for simple sbt projects: src/main|test/scala, target, project/target)
 * Without this class we would need to wait for the project reimport even to create a simple scala file in sources folder.
 *
 * @note all paths are relative to model content root
 */
final case class DefaultModuleContentEntryFolders(
  sources: Seq[String],
  testSources: Seq[String],
  resources: Seq[String],
  testResources: Seq[String],
  excluded: Seq[String] = DefaultModuleContentEntryFolders.RootTargets,
)

object DefaultModuleContentEntryFolders {
  val RootTargets = Seq("target", "project/target")

  val rootTargets: DefaultModuleContentEntryFolders =
    DefaultModuleContentEntryFolders(Nil, Nil, Nil, Nil, RootTargets)
}