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

import com.intellij.facet.ui.libraries.LibraryInfo;
import com.intellij.facet.ui.FacetBasedFrameworkSupportProvider;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.ide.util.frameworkSupport.FrameworkVersion;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.scala.ScalaBundle;

import java.util.ArrayList;
import java.util.List;

/**
 * @author ilyas
 */
public class ScalaFacetSupportProvider extends FacetBasedFrameworkSupportProvider<ScalaFacet> {

  private static final Logger LOG = Logger.getInstance("#org.jetbrains.plugins.scala.config.ScalaFacetSupportProvider");

  protected ScalaFacetSupportProvider() {
    super(ScalaFacetType.INSTANCE);
  }

  private static String getLibraryName(final String name) {
    return "scala-" + name;
  }

  @NonNls
  public String getTitle() {
    return ScalaBundle.message("scala.facet.title");
  }

  @Override
   protected void setupConfiguration(ScalaFacet facet, ModifiableRootModel rootModel, FrameworkVersion version) {
    //do nothing
  }

  @NotNull
  public List<FrameworkVersion> getVersions() {
    List<FrameworkVersion> versions = new ArrayList<FrameworkVersion>();
    for (ScalaVersion version : ScalaVersion.values()) {
      versions.add(new FrameworkVersion(version.toString(), getLibraryName(version.toString()), getLibraries(version.toString())));
    }
    return versions;
  }

  private static ScalaVersion getVersion(String versionName) {
    for (ScalaVersion version : ScalaVersion.values()) {
      if (versionName.equals(version.toString())) {
        return version;
      }
    }
    LOG.error("invalid scala version: " + versionName);
    return null;
  }

  private static LibraryInfo[] getLibraries(final String selectedVersion) {
    ScalaVersion version = getVersion(selectedVersion);
    LOG.assertTrue(version != null);
    return version.getJars();
  }

}