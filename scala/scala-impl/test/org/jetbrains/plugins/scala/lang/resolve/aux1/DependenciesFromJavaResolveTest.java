package org.jetbrains.plugins.scala.lang.resolve.aux1;

import com.intellij.psi.*;
import org.jetbrains.plugins.scala.lang.psi.api.base.ScReference;
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScClass;
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveResult;
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveTestCase;
import scala.Option;

import java.nio.file.Path;

public class DependenciesFromJavaResolveTest extends ScalaResolveTestCase {

  @Override
  public Path folderPath() {
    return super.folderPath().resolve("resolve").resolve("aux1").resolve("idea").resolve("test");
  }

  @Override
  public Path sourceRootPath() {
    return super.folderPath().resolve("resolve").resolve("aux1").resolve("idea");
  }

  public void testJavaFileWithNameTestJava() {
    PsiReference ref = findReferenceAtCaret();
    final PsiElement resolved = ref.resolve();

    assertTrue(resolved instanceof ScClass);

    final ScClass clazz = (ScClass) resolved;
    final PsiClass[] supers = clazz.getSupers();

    assertEquals(1, supers.length);
    final String name = supers[0].getName();
    assertEquals("Object", name);
  }
  
  public void testScalaPrivateTag() {
    PsiReference ref = findReferenceAtCaret();
    final PsiElement resolved = ref.resolve();
    assertNotNull(resolved);
    assertTrue(resolved instanceof PsiMethod);
  }

  public void testSCL6402() {
    final PsiReference ref = findReferenceAtCaret();
    final ScReference refElement = (ScReference) ref.getElement();
    final Option<ScalaResolveResult> bind = refElement.bind();
    assertTrue(bind.isDefined() && bind.get().isAccessible());
  }

  public void testScalaPublicTag1() {
    PsiReference ref = findReferenceAtCaret();
    final PsiElement resolved = ref.resolve();
    assertNotNull(resolved);
    assertTrue(resolved instanceof PsiField);
  }

  public void testScalaPublicTag2() {
    PsiReference ref = findReferenceAtCaret();
    final PsiElement resolved = ref.resolve();
    assertNotNull(resolved);
    assertTrue(resolved instanceof PsiMethod);
  }

  public void testJavaArrayTypeParameterInference() {
    PsiReference ref = findReferenceAtCaret();
    final PsiElement resolved = ref.resolve();
    assertNotNull(resolved);
    assert(resolved instanceof PsiMethod);
  }
}
