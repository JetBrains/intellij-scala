package org.jetbrains.plugins.scala.lang.psi.stubs.index;

import com.intellij.psi.PsiClass;
import com.intellij.psi.stubs.StubIndexKey;
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.packaging.ScPackageContainer;

/**
 * @author ilyas
 */
public interface ScalaIndexKeys {

    StubIndexKey<String, PsiClass> SHORT_NAME_KEY = StubIndexKey.createIndexKey("sc.class.shortName");
    StubIndexKey<Integer, PsiClass> FQN_KEY = StubIndexKey.createIndexKey("sc.class.fqn");
    StubIndexKey<Integer, ScPackageContainer> PACKAGE_FQN_KEY = StubIndexKey.createIndexKey("sc.package.fqn");
}
