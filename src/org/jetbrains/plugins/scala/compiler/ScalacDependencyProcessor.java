package org.jetbrains.plugins.scala.compiler;

import com.intellij.compiler.impl.javaCompiler.DependencyProcessor;
import com.intellij.compiler.make.CacheCorruptedException;
import com.intellij.compiler.make.DependencyCache;
import com.intellij.openapi.compiler.CompileContext;
import com.intellij.openapi.compiler.ex.CompileContextEx;
import com.intellij.openapi.diagnostic.Logger;

/**
 * @author ilyas
 */
class ScalacDependencyProcessor implements DependencyProcessor {

  private static final Logger LOG = Logger.getInstance("#org.jetbrains.plugins.scala.compiler.ScalacDependencyProcessor");

  public void processDependencies(CompileContext context, int classQualifiedName) {
    final CompileContextEx contextEx = (CompileContextEx) context;
    final DependencyCache cache = contextEx.getDependencyCache();
    try {
      cache.resolve(classQualifiedName);
    } catch (CacheCorruptedException e) {
      LOG.info(e);
    }
  }
}
