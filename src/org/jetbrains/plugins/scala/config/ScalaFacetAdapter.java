package org.jetbrains.plugins.scala.config;

import com.intellij.facet.Facet;
import com.intellij.openapi.module.Module;

/**
 * @author Alexander Podkhalyuzin
 */
public abstract class ScalaFacetAdapter extends Facet<ScalaFacetConfiguration> {
  public ScalaFacetAdapter(@org.jetbrains.annotations.NotNull Module module,
                           @org.jetbrains.annotations.NotNull String name,
                           @org.jetbrains.annotations.NotNull ScalaFacetConfiguration configuration,
                           Facet underlyingFacet) {
    super(ScalaFacet.Type(), module, name, configuration, underlyingFacet);
  }
}
