package org.jetbrains.plugins.scala.lang.resolve.aux1;

import com.intellij.psi.*;
import org.jetbrains.plugins.scala.lang.psi.api.base.ScReference;
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScClass;
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveResult;
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveTestCase;
import scala.Option;

/**
 * User: Alexander Podkhalyuzin
 * Date: 01.11.11
 */
public class DependenciesFromJavaResolveTest extends ScalaResolveTestCase {
  public String folderPath() {
    return super.folderPath() + "resolve/aux1/idea/test";
  }

  @Override
  public String sourceRootPath() {
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
    assertTrue(name.equals("Object"));
  }
  
  public void testScalaPrivateTag() throws Exception {
    PsiReference ref = findReferenceAtCaret();
    final PsiElement resolved = ref.resolve();
    assertNotNull(resolved);
    assertTrue(resolved instanceof PsiMethod);
  }

  public void testSCL6402() throws Exception {
    final PsiReference ref = findReferenceAtCaret();
    final ScReference refElement = (ScReference) ref.getElement();
    final Option<ScalaResolveResult> bind = refElement.bind();
    assertTrue(bind.isDefined() && bind.get().isAccessible());
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

  public void testJavaArrayTypeParameterInference() throws Exception {
    PsiReference ref = findReferenceAtCaret();
    final PsiElement resolved = ref.resolve();
    assertNotNull(resolved);
    assert(resolved instanceof PsiMethod);
  }
}
