package org.jetbrains.plugins.scala.debugger;

import com.intellij.debugger.NameMapper;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.Computable;
import com.intellij.psi.PsiClass;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScObject;
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTrait;
import org.jetbrains.plugins.scala.util.ScalaUtils;

/**
 * @author ilyas
 */
public class ScalaJVMNameMapper implements NameMapper {

  public String getQualifiedName(@NotNull final PsiClass clazz) {
    String qualifiedName = ApplicationManager.getApplication().runReadAction(new Computable<String>() {public String compute() {
      return clazz.getQualifiedName();
    }
    });
    if (clazz instanceof ScObject) return qualifiedName + "$";
    if (clazz instanceof ScTrait) return qualifiedName + "$class";
    return qualifiedName;
  }
}

class StringContainer {
  public StringContainer(String s) {
    this.s = s;
  }
  private String s;

  public String getS() {
    return s;
  }

  public void setS(String s) {
    this.s = s;
  }
}
