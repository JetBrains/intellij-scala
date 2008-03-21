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

import com.intellij.facet.FacetManagerAdapter;
import com.intellij.facet.FacetManager;
import com.intellij.facet.Facet;
import com.intellij.openapi.module.ModuleComponent;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.LibraryOrderEntry;
import com.intellij.util.messages.MessageBusConnection;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.scala.config.ScalaConfiguration;

/**
 * @author ilyas
 */
public class ScalaFacetListener extends FacetManagerAdapter implements ModuleComponent {

  private MessageBusConnection myConnection;

  private Module myModule;

  public ScalaFacetListener(Module module) {
    myModule = module;
  }


  public void projectOpened() {

  }

  public void projectClosed() {

  }

  public void moduleAdded() {

  }

  @NonNls
  @NotNull
  public String getComponentName() {
    return "ScalaFacetListener";
  }

  public void initComponent() {
    myConnection = myModule.getMessageBus().connect();
    myConnection.subscribe(FacetManager.FACETS_TOPIC, new FacetManagerAdapter() {
      public void facetAdded(@NotNull Facet facet) {
        final ScalaConfiguration configuration = ScalaConfiguration.getInstance();
        Library scalaLib = configuration.getScalaLib();
        if (facet.getTypeId() == ScalaFacet.FACET_TYPE_ID && scalaLib != null) {
          ScalaConfiguration.addLibrary(scalaLib, facet.getModule());
        }

      }

      public void facetRemoved(@NotNull Facet facet) {
        Library scalaLib = ScalaConfiguration.getInstance().getScalaLib();
        if (facet.getTypeId() == ScalaFacet.FACET_TYPE_ID && scalaLib != null) {
          removeLib(scalaLib, facet.getModule());
        }
      }
    });

  }

  public void disposeComponent() {
    myConnection.disconnect();
  }

  private void removeLib(Library scalaLib, Module module) {
    final ModifiableRootModel moduleModel = ModuleRootManager.getInstance(module).getModifiableModel();
    final LibraryOrderEntry entry = moduleModel.findLibraryOrderEntry(scalaLib);
    if (entry != null) {
      moduleModel.removeOrderEntry(entry);
      moduleModel.commit();
    }
  }

}
