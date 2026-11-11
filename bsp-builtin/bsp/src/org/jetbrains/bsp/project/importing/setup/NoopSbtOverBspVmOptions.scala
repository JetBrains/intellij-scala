package org.jetbrains.bsp.project.importing.setup

/** The production implementation of [[SbtOverBspVmOptions]], which adds no options */
private final class NoopSbtOverBspVmOptions extends SbtOverBspVmOptions:
  override def vmOptions: Seq[String] = Seq.empty
