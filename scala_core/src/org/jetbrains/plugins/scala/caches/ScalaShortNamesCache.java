package org.jetbrains.plugins.scala.caches;

import com.intellij.psi.PsiClass;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PsiShortNamesCache;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author ilyas
 */
public interface ScalaShortNamesCache extends PsiShortNamesCache {
  @Nullable
  PsiClass getClassByFQName(@NotNull @NonNls String name, @NotNull GlobalSearchScope scope);

  @NotNull
  PsiClass[] getClassesByFQName(@NotNull @NonNls String fqn, @NotNull GlobalSearchScope scope);
}
