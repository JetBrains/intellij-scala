package org.jetbrains.plugins.scala.cache.module;

import com.intellij.openapi.module.Module;

/**
 * @author Ilya.Sergey
 */
public class ScalaModuleCachesImpl implements ScalaModuleCaches{

  private Module myModule;

  public ScalaModuleCachesImpl (Module module) {
    myModule = module;
  }

  public void init (boolean b){

  }

}
