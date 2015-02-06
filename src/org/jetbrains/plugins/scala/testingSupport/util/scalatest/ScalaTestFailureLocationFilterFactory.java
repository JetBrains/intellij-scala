package org.jetbrains.plugins.scala.testingSupport.util.scalatest;

import com.intellij.execution.filters.ExceptionFilterFactory;
import com.intellij.execution.filters.Filter;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.annotations.NotNull;

/**
 * @author Roman.Shein
 * @since 01.02.2015.
 */
public class ScalaTestFailureLocationFilterFactory implements ExceptionFilterFactory {
  @NotNull
  @Override
  public Filter create(@NotNull GlobalSearchScope searchScope) {
    return new ScalaTestFailureLocationFilter(searchScope);
  }
}
