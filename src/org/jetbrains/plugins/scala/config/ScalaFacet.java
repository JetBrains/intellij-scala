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

import com.intellij.facet.*;
import com.intellij.openapi.module.Module;
import org.jetbrains.annotations.NotNull;

/**
 * @author ilyas
 */
public class ScalaFacet extends Facet<org.jetbrains.plugins.scala.config.ScalaFacetConfiguration> {

  public static final String FACET_TYPE_ID_STRING = "scala";
  public final static FacetTypeId<ScalaFacet> ID = new FacetTypeId<ScalaFacet>(FACET_TYPE_ID_STRING);

  public ScalaFacet(@NotNull Module module) {
    this(FacetTypeRegistry.getInstance().findFacetType(FACET_TYPE_ID_STRING), module, "Scala", new org.jetbrains.plugins.scala.config.ScalaFacetConfiguration(), null);
  }


  public ScalaFacet(final FacetType facetType, final Module module, final String name, final org.jetbrains.plugins.scala.config.ScalaFacetConfiguration configuration, final Facet underlyingFacet) {
    super(facetType, module, name, configuration, underlyingFacet);
  }

  public static ScalaFacet getInstance(@NotNull Module module){
    return FacetManager.getInstance(module).getFacetByType(ID);
  }

}
