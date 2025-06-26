package org.jetbrains.plugins.scala.project.gradle

import org.jetbrains.plugins.gradle.util.GradleConstants
import org.jetbrains.plugins.scala.compiler.sync.ExternalSystemBasedProjectSyncHandler

private final class GradleProjectSyncHelper extends ExternalSystemBasedProjectSyncHandler(GradleConstants.SYSTEM_ID)
