package org.jetbrains.plugins.scala.lang.psi.impl.toplevel.synthetic;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.stubs.StubIndex;
import com.intellij.psi.util.PsiUtilBase;
import com.intellij.util.indexing.FileBasedIndex;
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.packaging.ScPackageContainer;
import org.jetbrains.plugins.scala.lang.psi.stubs.index.ScalaIndexKeys;

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
      if (!(element instanceof ScPackageContainer)) {
        VirtualFile faultyContainer = PsiUtilBase.getVirtualFile(element);
        LOG.error("Wrong Psi in Psi list: " + faultyContainer);
        if (faultyContainer != null && faultyContainer.isValid()) {
          FileBasedIndex.getInstance().requestReindex(faultyContainer);
        }
        return false;
      }
    }
    return true;
  }
}
