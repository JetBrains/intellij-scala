package org.jetbrains.plugins.scala.lang.parser.stress;

import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiElementFactory;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileFactory;
import com.intellij.psi.impl.DebugUtil;
import junit.framework.Assert;
import junit.framework.Test;
import org.jetbrains.plugins.scala.lang.parser.SourceTest;

/**
 * @author ilyas
 */
public class DragSearchTest extends SourceTest {


  public String transform(String testName, String fileText) throws Exception {
    JavaPsiFacade facade = JavaPsiFacade.getInstance(project);
    PsiElementFactory psiElementFactory = facade.getElementFactory();
    Assert.assertNotNull(psiElementFactory);
    Assert.assertNotNull(TEMP_FILE);
    Assert.assertNotNull(fileText);

    PsiFile psiFile = PsiFileFactory.getInstance(project).createFileFromText(TEMP_FILE, fileText);
    return DebugUtil.psiToString(psiFile, false);
  }

  public static Test suite() {
    return new DragSearchTest();
  }


}
