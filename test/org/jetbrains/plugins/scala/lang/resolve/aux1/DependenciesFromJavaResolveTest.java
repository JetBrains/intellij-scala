package org.jetbrains.plugins.scala.lang.resolve.aux1;

import com.intellij.psi.*;
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScClass;
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveTestCase;

/**
 * User: Alexander Podkhalyuzin
 * Date: 01.11.11
 */
public class DependenciesFromJavaResolveTest extends ScalaResolveTestCase {
  public String folderPath() {
    return super.folderPath() + "resolve/aux1/idea/test";
  }

  @Override
  protected String rootPath() {
    return super.folderPath() + "resolve/aux1/idea/";
  }

  public void testJavaFileWithNameTestJava() throws Exception {
    PsiReference ref = findReferenceAtCaret();
    final PsiElement resolved = ref.resolve();

    assertTrue(resolved instanceof ScClass);

    final ScClass clazz = (ScClass) resolved;
    final PsiClass[] supers = clazz.getSupers();

    assertTrue(supers.length == 1);
    final String name = supers[0].getName();
    assertTrue(name.equals("ScalaObject"));

  }
  
  public void testScalaPrivateTag() throws Exception {
    PsiReference ref = findReferenceAtCaret();
    final PsiElement resolved = ref.resolve();
    assertNotNull(resolved);
    assertTrue(resolved instanceof PsiMethod);
  }

  public void testScalaPublicTag1() throws Exception {
    PsiReference ref = findReferenceAtCaret();
    final PsiElement resolved = ref.resolve();
    assertNotNull(resolved);
    assertTrue(resolved instanceof PsiField);
  }

  public void testScalaPublicTag2() throws Exception {
    PsiReference ref = findReferenceAtCaret();
    final PsiElement resolved = ref.resolve();
    assertNotNull(resolved);
    assertTrue(resolved instanceof PsiMethod);
  }
}
