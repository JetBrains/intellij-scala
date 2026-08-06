package org.jetbrains.plugins.scala.compiler.sync

import org.jetbrains.sbt.project.SbtProjectSystem

private final class SbtProjectSyncHelper extends ExternalSystemBasedProjectSyncHandler(SbtProjectSystem.Id)
