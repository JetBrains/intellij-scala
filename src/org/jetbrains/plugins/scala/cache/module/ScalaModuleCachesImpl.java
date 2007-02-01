package org.jetbrains.plugins.scala.cache.module;

import com.intellij.openapi.module.Module;
import org.jetbrains.plugins.scala.cache.ScalaFilesCacheImpl;

/**
 * @author Ilya.Sergey
 */
public class ScalaModuleCachesImpl extends ScalaFilesCacheImpl implements ScalaModuleCaches {

  private Module myModule;

  public ScalaModuleCachesImpl (Module module) {
    super(module.getProject());
    myModule = module;
  }

  public void init (boolean b){
    super.init(b);
  }

}
