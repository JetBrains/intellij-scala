package org.jetbrains.plugins.scala.lang.parser;

import com.intellij.psi.*;
import com.intellij.psi.impl.DebugUtil;
import junit.framework.Assert;
import junit.framework.Test;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.plugins.scala.testcases.BaseScalaFileSetTestCase;

public class ParserTest extends BaseScalaFileSetTestCase {
  @NonNls
  private static final String DATA_PATH = "test/org/jetbrains/plugins/scala/lang/parser/data";

  public ParserTest() {
    super(DATA_PATH);
  }


  public String transform(String testName, String[] data) throws Exception {
    String fileText = data[0];
    PsiManager psiManager = PsiManager.getInstance(project);
    PsiElementFactory psiElementFactory = psiManager.getElementFactory();
    Assert.assertNotNull(psiElementFactory);
    Assert.assertNotNull(TEMP_FILE);
    Assert.assertNotNull(fileText);
    PsiFile psiFile = psiElementFactory.createFileFromText(TEMP_FILE, fileText);

   // System.out.println(DebugUtil.psiToString(psiFile, false));
    //System.out.println("fooooooooooooooo");

    String psiTree = DebugUtil.psiToString(psiFile, false);
    System.out.println("------------------------"+testName+"------------------------");
    System.out.println(psiTree);
    System.out.println("");
/*
    String strPsiTree = "";
    for(String str : outTree){
      strPsiTree += str;
    }
*/
    //System.out.println(strPsiTree);

//    Assert.assertEquals(strPsiTree, DebugUtil.psiToString(psiFile, false));

    //String psiLeafText = gatherTextFromPsiFile(psiFile);
    //Assert.assertEquals(fileText, psiLeafText);

    //return DebugUtil.psiToString(psiFile, false);
    return psiTree;

  }

    private String gatherTextFromPsiFile(PsiFile psiFile) {
//        final StringBuffer result = new StringBuffer();
//        PsiElementVisitor myVisitor = new ScalaElementVisitor() {
//            public void visitElement(PsiElement element) {
//// if child is leaf
//                if (element.getFirstChild() == null) {
//                    result.append(element.getText());
//                } else {
//                    element.acceptChildren(this);
//                }
//            }
//
//        };
//        psiFile.accept(myVisitor);
//        return result.toString();
      return "";
    }

  public static Test suite() {
    return new ParserTest();
  }

}


