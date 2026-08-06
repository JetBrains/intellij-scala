package org.jetbrains.sbt

object SbtVersionCapabilities {
  /**
   * Scala3 is only supported since sbt 1.5.0
   */
  val MinSbtVersionForScala3: SbtVersion = SbtVersion("1.5.0")

  /**
   * Since version 1.2.0, sbt supports injecting additional plugins to the sbt shell with a command.
   * This allows injecting plugins without messing with the user's global directory.
   * https://github.com/sbt/sbt/pull/4211
   */
  private val MinVersionForAddPluginCommand_Sbt1: SbtVersion = SbtVersion("1.2.0")
  private val MinVersionForAddPluginCommand_Sbt0: SbtVersion = SbtVersion("0.13.18")

  def isAddPluginCommandSupported(sbtVersion: SbtVersion): Boolean =
    sbtVersion >= SbtVersionCapabilities.MinVersionForAddPluginCommand_Sbt1 ||
      sbtVersion.isSbt0 && sbtVersion >= MinVersionForAddPluginCommand_Sbt0

  private val SinceSlashSyntax: SbtVersion = SbtVersion("1.1.0")

  def isSlashSyntaxSupported(version: SbtVersion): Boolean =
    version >= SinceSlashSyntax

  def collectionsSeqClassFqn(sbtVersion: SbtVersion): String =
    if (sbtVersion.isSbt2)
      "_root_.scala.collection.immutable.Seq"
    else
      "_root_.scala.collection.Seq"
}
