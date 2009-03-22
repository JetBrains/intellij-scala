/*
 * Copyright 2000-2008 JetBrains s.r.o.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jetbrains.plugins.scala.config;

import com.intellij.facet.impl.ui.FacetTypeFrameworkSupportProvider;
import com.intellij.facet.impl.ui.VersionConfigurable;
import com.intellij.facet.ui.ValidationResult;
import com.intellij.facet.ui.libraries.LibraryInfo;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.Function;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.ide.util.newProjectWizard.FrameworkSupportProvider;
import com.intellij.ide.util.newProjectWizard.FrameworkSupportModel;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.scala.ScalaBundle;
import org.jetbrains.plugins.scala.config.ui.ScalaFacetEditor;
import org.jetbrains.plugins.scala.config.ui.CreateLibraryDialog;
import org.jetbrains.plugins.scala.settings.ScalaApplicationSettings;
import org.jetbrains.plugins.scala.util.LibrariesUtil;

import javax.swing.*;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.ArrayList;

/**
 * @author ilyas
 */
public class ScalaFacetSupportProvider extends FacetTypeFrameworkSupportProvider<ScalaFacet> {

  private static final Logger LOG = Logger.getInstance("#org.jetbrains.plugins.scala.config.ScalaFacetSupportProvider");

  protected ScalaFacetSupportProvider() {
    super(ScalaFacetType.INSTANCE);
  }

  @NotNull
  @NonNls
  public String getLibraryName(final String name) {
    return "scala-" + name;
  }

  @NonNls
  public String getTitle() {
    return "Sca&la";
  }

  @NotNull
  public String[] getVersions() {
    List<String> versions = new ArrayList<String>();
    for (ScalaVersion version : ScalaVersion.values()) {
      versions.add(version.toString());
    }
    return versions.toArray(new String[versions.size()]);
  }

  private static ScalaVersion getVersion(String versionName) {
    for (ScalaVersion version : ScalaVersion.values()) {
      if (versionName.equals(version.toString())) {
        return version;
      }
    }
    LOG.error("invalid struts version: " + versionName);
    return null;
  }

  @NotNull
  protected LibraryInfo[] getLibraries(final String selectedVersion) {
    ScalaVersion version = getVersion(selectedVersion);
    LOG.assertTrue(version != null);
    return version.getJars();
  }


  protected void setupConfiguration(ScalaFacet facet, ModifiableRootModel rootModel, String v) {
    //do nothing
  }

}