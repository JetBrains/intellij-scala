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

import com.intellij.facet.Facet;
import com.intellij.facet.FacetModel;
import com.intellij.facet.FacetType;
import com.intellij.facet.autodetecting.DetectedFacetPresentation;
import com.intellij.facet.autodetecting.FacetDetector;
import com.intellij.facet.autodetecting.FacetDetectorRegistry;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.module.StdModuleTypes;
import com.intellij.openapi.roots.LibraryOrderEntry;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileFilter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.scala.bundle.ScalaBundle;
import org.jetbrains.plugins.scala.ScalaFileType;
import org.jetbrains.plugins.scala.icons.Icons;
import org.jetbrains.plugins.scala.settings.ScalaApplicationSettings;
import org.jetbrains.plugins.scala.util.LibrariesUtil;

import javax.swing.*;
import java.util.Collection;

/**
 * @author ilyas
 */
public class ScalaFacetType extends FacetType<ScalaFacet, ScalaFacetConfiguration> {

  public static final ScalaFacetType INSTANCE = new ScalaFacetType();

  public org.jetbrains.plugins.scala.config.ScalaFacetConfiguration createDefaultConfiguration() {
    return new org.jetbrains.plugins.scala.config.ScalaFacetConfiguration();
  }

  private ScalaFacetType() {
    super(ScalaFacet.ID, "Scala", "Scala");
  }

  public ScalaFacet createFacet(@NotNull Module module, String name, @NotNull org.jetbrains.plugins.scala.config.ScalaFacetConfiguration configuration, @Nullable Facet underlyingFacet) {
    return new ScalaFacet(this, module, name, configuration, underlyingFacet);
  }

  public Icon getIcon() {
    return Icons.FILE_TYPE_LOGO;
  }

  public boolean isOnlyOneFacetAllowed() {
    return true;
  }

  public boolean isSuitableModuleType(ModuleType moduleType) {
    return moduleType == StdModuleTypes.JAVA || "PLUGIN_MODULE".equals(moduleType.getId());
  }

    public void registerDetectors(final FacetDetectorRegistry<ScalaFacetConfiguration> registry) {
    FacetDetector<VirtualFile, ScalaFacetConfiguration> detector = new ScalaFacetDetector();

    final Ref<Boolean> alreadyDetected = new Ref<Boolean>(false);
    VirtualFileFilter grailsFacetFilter = new VirtualFileFilter() {
      public boolean accept(VirtualFile virtualFile) {
        if (alreadyDetected.get()) return true;
        alreadyDetected.set(true);
        if (ScalaFileType.SCALA_FILE_TYPE.getDefaultExtension().equals(virtualFile.getExtension())) {
          registry.customizeDetectedFacetPresentation(new ScalaFacetPresentation());
          return true;
        }

        return false;
      }
    };

    registry.registerUniversalDetector(ScalaFileType.SCALA_FILE_TYPE, grailsFacetFilter, detector);
  }

    private class ScalaFacetDetector extends FacetDetector<VirtualFile, ScalaFacetConfiguration> {

    public ScalaFacetConfiguration detectFacet(VirtualFile source, Collection<ScalaFacetConfiguration> existentFacetConfigurations) {
      if (!existentFacetConfigurations.isEmpty()) {
        return existentFacetConfigurations.iterator().next();
      }
      return createDefaultConfiguration();
    }

    public void beforeFacetAdded(@NotNull Facet facet, FacetModel facetModel, @NotNull ModifiableRootModel model) {
      LibraryTable libTable = LibraryTablesRegistrar.getInstance().getLibraryTable();
      String name = ScalaApplicationSettings.getInstance().DEFAULT_SCALA_LIB_NAME;
      if (name != null && libTable.getLibraryByName(name) != null) {
        Library library = libTable.getLibraryByName(name);
        if (ScalaConfigUtils.isScalaSdkLibrary(library)) {
          LibraryOrderEntry entry = model.addLibraryEntry(library);
          LibrariesUtil.placeEntryToCorrectPlace(model, entry);
        }
      }
    }
  }

  private static class ScalaFacetPresentation extends DetectedFacetPresentation {

    @Nullable
    public String getAutodetectionPopupText(@NotNull Facet facet, @NotNull VirtualFile[] files) {
      return ScalaBundle.message("new.scala.facet.detected");
    }
  }
}
