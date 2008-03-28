package org.jetbrains.plugins.scala.debugger;

import com.intellij.debugger.NameMapper;
import com.intellij.psi.PsiClass;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScObject;
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTrait;

/**
 * @author ilyas
 */
public class ScalaJVMNameMapper implements NameMapper {

  public String getQualifiedName(@NotNull PsiClass clazz) {
    String qualifiedName = clazz.getQualifiedName();
    if (clazz instanceof ScObject) return qualifiedName + "$";
    if (clazz instanceof ScTrait) return qualifiedName + "$class";
    return qualifiedName;
  }
}
