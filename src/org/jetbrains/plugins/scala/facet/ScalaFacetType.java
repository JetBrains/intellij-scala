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

package org.jetbrains.plugins.scala.facet;

import com.intellij.facet.FacetType;
import com.intellij.facet.Facet;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.module.StdModuleTypes;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.scala.ScalaIcons;

import javax.swing.*;

/**
 * @author ilyas
 */
public class ScalaFacetType extends FacetType<ScalaFacet, ScalaFacetConfiguration> {

  public static final ScalaFacetType INSTANCE = new ScalaFacetType();

  public ScalaFacetConfiguration createDefaultConfiguration() {
    return new ScalaFacetConfiguration();
  }

  private ScalaFacetType() {
    super(ScalaFacet.FACET_TYPE_ID, "Scala", "Scala");
  }

  public ScalaFacet createFacet(@NotNull Module module, String name, @NotNull ScalaFacetConfiguration configuration, @Nullable Facet underlyingFacet) {
    return new ScalaFacet(this, module, name, configuration, underlyingFacet);
  }

  public Icon getIcon() {
    return ScalaIcons.FILE_TYPE_LOGO;
  }

  public boolean isOnlyOneFacetAllowed() {
    return true;
  }

  public boolean isSuitableModuleType(ModuleType moduleType) {
    return moduleType == StdModuleTypes.JAVA || "PLUGIN_MODULE".equals(moduleType.getId());
  }
}
