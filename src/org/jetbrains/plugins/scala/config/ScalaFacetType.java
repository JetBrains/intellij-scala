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
import com.intellij.facet.FacetTypeRegistry;
import com.intellij.facet.autodetecting.DetectedFacetPresentation;
import com.intellij.facet.autodetecting.FacetDetector;
import com.intellij.facet.autodetecting.FacetDetectorRegistry;
import com.intellij.openapi.module.JavaModuleType;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileFilter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.scala.ScalaBundle;
import org.jetbrains.plugins.scala.ScalaFileType;
import org.jetbrains.plugins.scala.icons.Icons;

import javax.swing.*;
import java.util.Collection;

/**
 * @author ilyas
 */
public class ScalaFacetType extends FacetType<ScalaFacet, ScalaFacetConfiguration> {

  public static final ScalaFacetType INSTANCE = new ScalaFacetType();

  private ScalaFacetType() {
    super(ScalaFacet.ID, "Scala", "Scala");
  }

  public ScalaFacetConfiguration createDefaultConfiguration() {
    return new ScalaFacetConfiguration();
  }

  public ScalaFacet createFacet(@NotNull Module module,
                                 String name,
                                 @NotNull ScalaFacetConfiguration configuration,
                                 @Nullable Facet underlyingFacet) {
    return new ScalaFacet(this, module, name, configuration, underlyingFacet);
  }

  public Icon getIcon() {
    return Icons.FILE_TYPE_LOGO;
  }

  public boolean isSuitableModuleType(ModuleType moduleType) {
    return (moduleType instanceof JavaModuleType || "PLUGIN_MODULE".equals(moduleType.getId()));
  }

  public void registerDetectors(final FacetDetectorRegistry<ScalaFacetConfiguration> registry) {
    FacetDetector<VirtualFile, ScalaFacetConfiguration> detector = new ScalaFacetDetector();

    final Ref<Boolean> alreadyDetected = new Ref<Boolean>(false);
    VirtualFileFilter grailsFacetFilter = new VirtualFileFilter() {
      public boolean accept(VirtualFile virtualFile) {
        if (alreadyDetected.get()) return true;
        alreadyDetected.set(true);
        if (ScalaFileType.DEFAULT_EXTENSION.equals(virtualFile.getExtension())) {
          registry.customizeDetectedFacetPresentation(new ScalaFacetPresentation());
          return true;
        }

        return false;
      }
    };

    registry.registerUniversalDetector(ScalaFileType.SCALA_FILE_TYPE, grailsFacetFilter, detector);
  }

  public static ScalaFacetType getInstance() {
    final ScalaFacetType facetType = (ScalaFacetType) FacetTypeRegistry.getInstance().findFacetType(ScalaFacet.ID);
    assert facetType != null;
    return facetType;
  }

  private class ScalaFacetDetector extends FacetDetector<VirtualFile, ScalaFacetConfiguration> {
    public ScalaFacetDetector() {
      super("scala-detector");
    }

    public ScalaFacetConfiguration detectFacet(VirtualFile source, Collection<ScalaFacetConfiguration> existentFacetConfigurations) {
      if (!existentFacetConfigurations.isEmpty()) {
        return existentFacetConfigurations.iterator().next();
      }
      return createDefaultConfiguration();
    }

    public void beforeFacetAdded(@NotNull Facet facet, FacetModel facetModel, @NotNull ModifiableRootModel model) {
    }
  }

  private static class ScalaFacetPresentation extends DetectedFacetPresentation {

    @Nullable
    public String getAutodetectionPopupText(@NotNull Facet facet, @NotNull VirtualFile[] files) {
      return ScalaBundle.message("new.scala.facet.detected");
    }
  }

}