package org.jetbrains.plugins.scala.codeInspection.bundled;

import com.intellij.openapi.components.*;
import com.intellij.openapi.project.Project;
import com.intellij.util.xmlb.XmlSerializerUtil;

import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * User: Dmitry.Naydanov
 * Date: 04.10.16.
 */
@State(
    name = "ScalaBundledInspectionsPersistentState",
    storages = {@Storage(StoragePathMacros.WORKSPACE_FILE)}
)
public class BundledInspectionsPersistentState implements PersistentStateComponent<BundledInspectionsPersistentState> {
  public String[] disabledInspectionsId = new String[]{};
  private Map<String, ArrayList<String>> libToInspectionsNames = new HashMap<String, ArrayList<String>>();
  
  @Nullable
  @Override
  public BundledInspectionsPersistentState getState() {
    return this;
  }

  @Override
  public void loadState(BundledInspectionsPersistentState state) {
    XmlSerializerUtil.copyBean(state, this);
  }
  
  public static BundledInspectionsPersistentState getInstance(Project project) {
    return ServiceManager.getService(project, BundledInspectionsPersistentState.class);
  }

  public Map<String, ArrayList<String>> getLibToInspectionsNames() {
    return libToInspectionsNames;
  }

  public void setLibToInspectionsNames(Map<String, ArrayList<String>> libToInspectionsNames) {
    this.libToInspectionsNames = libToInspectionsNames;
  }
}
