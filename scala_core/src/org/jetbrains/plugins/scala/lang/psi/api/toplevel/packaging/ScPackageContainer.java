package org.jetbrains.plugins.scala.lang.psi.api.toplevel.packaging;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiPackage;

/**
 * @author ilyas
 */
public interface ScPackageContainer extends PsiElement{

  String fqn();

  PsiPackage getSyntheticPackage(String fqn);

}
