package org.jetbrains.plugins.scala.testingSupport.util.scalatest;

import com.intellij.execution.filters.ExceptionFilterFactory;
import com.intellij.execution.filters.Filter;
import com.intellij.openapi.project.Project;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

public class ScalaTestFailureLocationFilterFactory implements ExceptionFilterFactory {
  @NotNull
  @Override
  public Filter create(@NotNull GlobalSearchScope searchScope) {
    return create(Objects.requireNonNull(searchScope.getProject()), searchScope);
  }

  @NotNull
  @Override
  public Filter create(@NotNull Project project, @NotNull GlobalSearchScope searchScope) {
    return new ScalaTestFailureLocationFilter(project, searchScope);
  }
}
