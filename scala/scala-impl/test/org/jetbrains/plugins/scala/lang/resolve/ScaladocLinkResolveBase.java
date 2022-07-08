package org.jetbrains.plugins.scala.lang.resolve;

import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import org.jetbrains.plugins.scala.lang.psi.api.base.ScReference;
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScNamedElement;
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTemplateDefinition;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

abstract public class ScaladocLinkResolveBase extends ScalaResolveTestCase {
  protected static final String testRef = "<testref>";

  @Override
  public String folderPath() {
    return super.folderPath() + "resolve/scaladoc";
  }

  protected List<Integer> getAllRef() throws IOException {
    String filePath = folderPath() + File.separator + getTestName(false) + ".scala";
    StringBuilder fileText =
        new StringBuilder(StringUtil.convertLineSeparators(FileUtil.loadFile(new File(filePath), CharsetToolkit.UTF8)));
    ArrayList<Integer> list = new ArrayList<Integer>();
    int i = 0;
    while ((i = fileText.indexOf(testRef, i)) != -1) {
      list.add(i);
      fileText.replace(i, i + testRef.length(), "");
    }

    configureFromFileTextAdapter(getTestName(false) + ".scala", fileText.toString());
    return list;
  }

  protected void genericResolve(int expectedLength, Class<?> expectedClass) throws IOException {
    List<Integer> carets = getAllRef();

    for (int i : carets) {
      PsiReference ref = getFileAdapter().findReferenceAt(i);
      ScalaResolveResult[] results = ((ScReference) ref).multiResolveScala(false);
      assertSize(expectedLength, results);

      if (expectedLength == 0) {
        continue;
      }

      PsiElement resolved = results[0].getElement();
      assertTrue(expectedClass.isAssignableFrom(resolved.getClass()));

      final String refText = ((ScReference) ref).getText();
      if (expectedClass == PsiClass.class) {
        final PsiClass resolved1 = (PsiClass) resolved;
        String qualifiedName = resolved1.getQualifiedName();
        String name = resolved1.getName();
        
        if (resolved instanceof ScTemplateDefinition) qualifiedName = ((ScTemplateDefinition) resolved).qualifiedName();
        assertTrue(qualifiedName.equals(refText) || name.equals(refText));
      } else {
        assertTrue(((ScNamedElement) resolved).getName().equals(refText));
      }
    }
  }

  protected void genericResolve(int expectedLength) throws IOException {
    genericResolve(expectedLength, PsiClass.class);
  }
}
