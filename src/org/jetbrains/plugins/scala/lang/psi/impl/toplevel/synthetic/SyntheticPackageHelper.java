package org.jetbrains.plugins.scala.lang.psi.impl.toplevel.synthetic;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.stubs.StubIndex;
import org.jetbrains.plugins.scala.lang.psi.stubs.index.ScalaIndexKeys;
import org.jetbrains.plugins.scala.lang.psi.stubs.util.ScalaStubsUtil;

import java.util.Collection;

/**
 * User: Alexander Podkhalyuzin
 * Date: 01.03.2010
 */
public class SyntheticPackageHelper {
  private static Logger LOG =
      Logger.getInstance("#org.jetbrains.plugins.scala.lang.psi.impl.toplevel.synthetic.SyntheticPackageHelper");

  static boolean checkNeedReindex(Project project, String fqn) {
    Collection<? extends PsiElement > collection = StubIndex.getInstance().get(ScalaIndexKeys.PACKAGE_FQN_KEY(),
      fqn.hashCode(), project, GlobalSearchScope.allScope(project));

    for (PsiElement element : collection) {
      if (!ScalaStubsUtil.checkPsiForPackageContainer(element)) return false;
    }
    return true;
  }
}
