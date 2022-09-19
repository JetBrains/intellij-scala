package org.jetbrains.sbt.project;

import com.intellij.openapi.externalSystem.model.ProjectSystemId;
import org.jetbrains.plugins.scala.NlsString;
import org.jetbrains.sbt.Sbt;

public final class SbtProjectSystem {
    public static final ProjectSystemId Id = new ProjectSystemId("SBT", NlsString.force(Sbt.Name()));
}
