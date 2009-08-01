package org.jetbrains.plugins.scala.lang.resolve;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiReference;
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScPattern;
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction;
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScTypeAlias;
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScTypeAliasDeclaration;
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScClass;
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTypeDefinition;
import org.jetbrains.plugins.scala.util.TestUtils;

public class PackageObjectResolveTest extends ScalaResolveTestCase{

  public String getTestDataPath() {
    return TestUtils.getTestDataPath() + "/resolve/";
  }

  public void testScalaCollectionFullyQualified() throws Exception {
    PsiReference ref = configureByFile("packageObject/scalaCollectionFullyQualified.scala");
    assertTrue(ref.resolve() instanceof ScTypeDefinition);
  }

  public void testScalaCollectionViaPackageObject() throws Exception {
    PsiReference ref = configureByFile("packageObject/scalaCollectionViaPackageObject.scala");
    PsiElement resolved = ref.resolve();
    assertTrue(resolved instanceof ScTypeAlias);
  }
}