package org.jetbrains.plugins.scala.lang.resolve;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTemplateDefinition;

public class ResolveClassLevelImportTest extends ScalaResolveTestCase {
  @Override
  public String folderPath() {
    return super.folderPath() + "resolve/class/classLevelImport/";
  }

  public void testclassLevelImport() throws Exception {
    PsiReference ref = findReferenceAtCaret();
    PsiElement resolved = ref.resolve();
    assertTrue(resolved instanceof ScTemplateDefinition);
    assertEquals(((ScTemplateDefinition) resolved).qualifiedName(), "scala.collection.immutable.Map");
  }
}
