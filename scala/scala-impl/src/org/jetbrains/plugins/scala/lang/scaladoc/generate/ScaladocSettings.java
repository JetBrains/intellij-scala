package org.jetbrains.plugins.scala.lang.scaladoc.generate;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.project.Project;
import com.intellij.util.xmlb.XmlSerializerUtil;

/**
 * User: Dmitry Naidanov
 * Date: 22.10.11
 */

@State(name = "ScaladocSettings")
public class ScaladocSettings implements PersistentStateComponent<ScaladocSettings> {

  public String outputDir = null;
  public String additionalFlags = null;
  public String maxHeapSize = null;
  public String docTitle = null;
  public Boolean verbose = null;
  public Boolean openInBrowser = null;

  public ScaladocSettings getState() {
    return this;
  }

  public void loadState(ScaladocSettings state) {
    XmlSerializerUtil.copyBean(state, this);
  }

  public static ScaladocSettings getInstance(Project project) {
    return project.getService(ScaladocSettings.class);
  }

}
