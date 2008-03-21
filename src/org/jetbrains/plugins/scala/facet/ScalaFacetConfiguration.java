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

import com.intellij.facet.FacetConfiguration;
import com.intellij.facet.ui.FacetEditorContext;
import com.intellij.facet.ui.FacetEditorTab;
import com.intellij.facet.ui.FacetEditorsFactory;
import com.intellij.facet.ui.FacetValidatorsManager;
import com.intellij.facet.ui.libraries.FacetLibrariesValidator;
import com.intellij.facet.ui.libraries.FacetLibrariesValidatorDescription;
import com.intellij.facet.ui.libraries.LibraryInfo;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import org.jdom.Element;

/**
 * @author ilyas
 */
public class ScalaFacetConfiguration implements FacetConfiguration {

  public FacetEditorTab[] createEditorTabs(final FacetEditorContext editorContext, final FacetValidatorsManager validatorsManager) {

//    LibraryInfo[] libraryInfos = {new LibraryInfo("javaee.jar", "5", "lib://javaee.jar", "Local Libraries", "javax.persistence.Entity")};
    final FacetLibrariesValidator validator = FacetEditorsFactory.getInstance().
        createLibrariesValidator(LibraryInfo.EMPTY_ARRAY,
            new FacetLibrariesValidatorDescription("scala"),
            editorContext,
            validatorsManager);

    final ScalaConfigurationTab configurationTab = new ScalaConfigurationTab(editorContext);
    validatorsManager.registerValidator(validator);
    return new FacetEditorTab[]{configurationTab};
  }


  public void readExternal(Element element) throws InvalidDataException {

  }

  public void writeExternal(Element element) throws WriteExternalException {

  }
}
