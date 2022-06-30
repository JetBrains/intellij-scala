package org.jetbrains.plugins.scala.lang.scaladoc.generate;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.project.Project;
import com.intellij.util.xmlb.XmlSerializerUtil;
import org.jetbrains.annotations.NotNull;


@State(
        name = "ScaladocSettings",
        reportStatistic = true
)
public class ScaladocSettings implements PersistentStateComponent<ScaladocSettings> {

  public String outputDir = null;
  public String additionalFlags = null;
  public String maxHeapSize = null;
  public String docTitle = null;
  public Boolean verbose = null;
  public Boolean openInBrowser = null;

  @Override
  public ScaladocSettings getState() {
    return this;
  }

  @Override
  public void loadState(@NotNull ScaladocSettings state) {
    XmlSerializerUtil.copyBean(state, this); // TODO: from docs:  defensive copying is not required.
  }

  public static ScaladocSettings getInstance(Project project) {
    return project.getService(ScaladocSettings.class);
  }

}
