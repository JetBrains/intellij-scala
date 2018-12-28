package org.jetbrains.plugins.scala.lang.resolve;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.psi.PsiClass;
import com.intellij.psi.search.GlobalSearchScope;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * User: Dmitry.Naydanov
 * Date: 08.09.14.
 */
public abstract class SyntheticClassProducer {
  public static ExtensionPointName<SyntheticClassProducer> EP_NAME =
      ExtensionPointName.create("org.intellij.scala.scalaSyntheticClassProducer");

  public static SyntheticClassProducer defaultProducer = new SyntheticClassProducer() {
    @Override
    public PsiClass[] findClasses(String fqn, GlobalSearchScope scope) {
      return new PsiClass[0];
    }
  };

  private static SyntheticClassProducer[] allProducers = null;

  private static SyntheticClassProducer[] getAllProducers() {
    if (allProducers == null) {
      allProducers = EP_NAME.getExtensions();
    }
    return allProducers;
  }

  public abstract PsiClass[] findClasses(String fqn, GlobalSearchScope scope);

  public static PsiClass[] getAllClasses(String fqn, GlobalSearchScope scope) {
    List<PsiClass> all = new ArrayList<PsiClass>();

    for (SyntheticClassProducer ex : getAllProducers()) {
      all.addAll(Arrays.asList(ex.findClasses(fqn, scope)));
    }

    return all.toArray(new PsiClass[all.size()]);
  }
}
