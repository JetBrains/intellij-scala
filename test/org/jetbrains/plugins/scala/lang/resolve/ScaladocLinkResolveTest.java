package org.jetbrains.plugins.scala.lang.resolve;

import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import org.jetbrains.plugins.scala.lang.psi.api.base.ScReferenceElement;
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScNamedElement;
import org.jetbrains.plugins.scala.lang.psi.impl.statements.params.ScParameterImpl;
import org.jetbrains.plugins.scala.lang.psi.impl.statements.params.ScTypeParamImpl;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * User: Dmitry Naydanov
 * Date: 12/5/11
 */
public class ScaladocLinkResolveTest extends ScalaResolveTestCase {
  @Override
  public String folderPath() {
    return super.folderPath() + "resolve/scaladoc";
  }

  protected List<Integer> getAllRef() throws IOException {
    final String testRef = "<testref>";
    
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
  
  private void genericResolve(int expectedLength, Class<?> expectedClass) throws IOException {
    List<Integer> carets = getAllRef();
    
    for (int i : carets) {
      PsiReference ref = getFileAdapter().findReferenceAt(i);
      assertSize(expectedLength, ((ScReferenceElement) ref).multiResolve(false));

      if (expectedLength == 0) {
        continue;
      }

      PsiElement resolved = ((ScReferenceElement) ref).multiResolve(false)[0].getElement();
      assertTrue(expectedClass.isAssignableFrom(resolved.getClass()));

      if (expectedClass == PsiClass.class) {
        assertTrue(((PsiClass) resolved).getQualifiedName().equals(((ScReferenceElement) ref).getText()));
      } else {
        assertTrue(((ScNamedElement) resolved).getName().equals(((ScReferenceElement) ref).getText()));
      }

    }
  }

  private void genericResolve(int expectedLength) throws IOException {
    genericResolve(expectedLength, PsiClass.class);
  }

  public void testCodeLinkNoResolve() throws Exception {
    genericResolve(0);
  }

  public void testCodeLinkResolve() throws Exception {
    genericResolve(1);
  }

  public void testCodeLinkMultiResolve() throws Exception {
    genericResolve(2);
  }

  public void testMethodParamResolve() throws Exception {
    genericResolve(1, ScParameterImpl.class);
  }

  public void testMethodTypeParamResolve() throws Exception {
    genericResolve(1, ScTypeParamImpl.class);
  }

  public void testMethodParamNoResolve() throws Exception {
    genericResolve(0);
  }

  public void testPrimaryConstrParamResolve() throws Exception {
    genericResolve(1, ScParameterImpl.class);
  }

  public void testPrimaryConstrTypeParamResolve() throws Exception {
    genericResolve(1, ScTypeParamImpl.class);
  }

  public void testPrimaryConstrParamNoResolve() throws Exception {
    genericResolve(0);
  }

  public void testPrimaryConstrTypeParamNoResolve() throws Exception {
    genericResolve(0);
  }

  public void testTypeAliasParamResolve() throws Exception {
    genericResolve(1, ScTypeParamImpl.class);
  }

  public void testTypeAliasParamNoResolve() throws Exception {
    genericResolve(0);
  }
}
