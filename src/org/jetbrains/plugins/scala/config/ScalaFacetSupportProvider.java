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
import com.intellij.facet.ui.ValidationResult;
import com.intellij.facet.ui.libraries.LibraryInfo;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.project.Project;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
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

/**
 * @author ilyas
 */
public class ScalaFacetSupportProvider extends FacetTypeFrameworkSupportProvider<ScalaFacet> {

  protected ScalaFacetSupportProvider() {
    super(ScalaFacetType.INSTANCE);
  }

  @NotNull
  public ScalaVersionConfigurable createConfigurable(final Project project) {
    return new ScalaVersionConfigurable(project, getDefaultVersion());
  }

  @Nullable
  public String getDefaultVersion() {
    ScalaApplicationSettings settings = ScalaApplicationSettings.getInstance();
    return settings.DEFAULT_SCALA_LIB_NAME;
  }

  @NotNull
  public String[] getVersions() {
    String[] versions =
      ContainerUtil.map2Array(ScalaConfigUtils.getGlobalScalaLibraries(), String.class, new Function<Library, String>() {
        public String fun(Library library) {
          return library.getName();
        }
      });
    Arrays.sort(versions, new Comparator<String>() {
      public int compare(String o1, String o2) {
        return -o1.compareToIgnoreCase(o2);
      }
    });
    return versions;
  }

  @NotNull
  @NonNls
  public String getLibraryName(final String name) {
    return name != null ? name : super.getLibraryName(name);
  }

  @NotNull
  public LibraryInfo[] getLibraries(String selectedVersion) {
    return super.getLibraries(selectedVersion);
  }

  protected void setupConfiguration(ScalaFacet facet, ModifiableRootModel rootModel, String v) {
    /**
     * Everyting is already done in {@link ScalaVersionConfigurable#addSupport}
     * */
  }

  public class ScalaVersionConfigurable extends VersionConfigurable {
    public final ScalaFacetEditor myFacetEditor;

    public JComponent getComponent() {
      return myFacetEditor.createComponent();
    }

    @NotNull
    public LibraryInfo[] getLibraries() {
      final Library lib = myFacetEditor.getSelectedLibrary();
      return ScalaFacetSupportProvider.this.getLibraries(lib != null ? lib.getName() : null);
    }

    @Nullable
    public String getNewSdkPath() {
      return myFacetEditor.getNewSdkPath();
    }

    public boolean addNewSdk() {
      return myFacetEditor.addNewSdk();
    }

    @NonNls
    @NotNull
    public String getLibraryName() {
      final Library lib = myFacetEditor.getSelectedLibrary();
      return ScalaFacetSupportProvider.this.getLibraryName(lib != null ? lib.getName() : null);
    }

    public void addSupport(final Module module, final ModifiableRootModel rootModel, @Nullable Library library) {
      Library selectedLibrary = myFacetEditor.getSelectedLibrary();
      String selectedName = null;
      if (selectedLibrary != null && !myFacetEditor.addNewSdk()) {
        selectedName = selectedLibrary.getName();
        LibrariesUtil.placeEntryToCorrectPlace(rootModel, rootModel.addLibraryEntry(selectedLibrary));
      } else if (myFacetEditor.getNewSdkPath() != null) {
        final String path = myFacetEditor.getNewSdkPath();
        ValidationResult result = ScalaConfigUtils.isScalaSdkHome(path);
        if (path != null && ValidationResult.OK == result) {
          final Project project = module.getProject();
          selectedName = ScalaConfigUtils.generateNewScalaLibName(ScalaConfigUtils.getScalaVersion(path), project);
          final CreateLibraryDialog dialog = new CreateLibraryDialog(project, selectedName);
          dialog.show();
          if (dialog.isOK()) {
            final Library lib = ScalaConfigUtils.createScalaLibrary(path, selectedName, project, false, dialog.isInProject());
            LibrariesUtil.placeEntryToCorrectPlace(rootModel, rootModel.addLibraryEntry(lib));
          }
        } else {
          Messages.showErrorDialog(ScalaBundle.message("invalid.scala.sdk.path.message"), ScalaBundle.message("invalid.scala.sdk.path.text"));
          selectedLibrary = null;
        }
      }
      if (selectedLibrary != null) {
        ScalaConfigUtils.saveScalaDefaultLibName(selectedName);
      }
      ScalaFacetSupportProvider.this.addSupport(module, rootModel, selectedName, selectedLibrary);
    }

    public ScalaVersionConfigurable(Project project, String defaultVersion) {
      super(getVersions(), defaultVersion);
      myFacetEditor = new ScalaFacetEditor(project, getDefaultVersion());
    }
  }
}