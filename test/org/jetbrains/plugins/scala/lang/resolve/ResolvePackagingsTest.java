package org.jetbrains.plugins.scala.lang.resolve;

import org.jetbrains.plugins.scala.lang.psi.api.base.ScPrimaryConstructor;
import org.jetbrains.plugins.scala.util.TestUtils;
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScClass;
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScTypeAliasDefinition;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiClass;

/**
 * @author ilyas
 */
public class ResolvePackagingsTest extends ScalaResolveTestCase {
  public String getTestDataPath() {
    return TestUtils.getTestDataPath() + "/resolve/packages/";
  }

  @Override
   public boolean allSourcesFromDirectory() {
    return true;
  }

  public void testSeparatedPackages() throws Exception {
    PsiReference ref = configureByFile("separated/my/scala/stuff/Main.scala");
    final PsiElement psiElement = ref.resolve();
    assertTrue(psiElement instanceof ScPrimaryConstructor);
    final ScPrimaryConstructor aClass = (ScPrimaryConstructor) psiElement;
    assertEquals(aClass.getContainingClass().getQualifiedName(),"my.scala.List");
  }

  public void testSolidPackages() throws Exception {
    PsiReference ref = configureByFile("solid/my/scala/stuff/Main.scala");
    final PsiElement psiElement = ref.resolve();
    assertTrue(psiElement instanceof ScPrimaryConstructor);
  }

}
