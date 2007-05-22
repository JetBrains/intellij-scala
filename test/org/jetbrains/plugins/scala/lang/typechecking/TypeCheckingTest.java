/*
 * Copyright (c) 2007, Your Corporation. All Rights Reserved.
 */

package org.jetbrains.plugins.scala.lang.typechecking;

import org.jetbrains.plugins.scala.lang.actions.ActionTest;
import org.jetbrains.plugins.scala.lang.actions.editor.enter.EnterActionTest;
import org.jetbrains.plugins.scala.lang.psi.impl.expressions.IScalaExpression;
import org.jetbrains.plugins.scala.lang.typechecker.IScalaTypeChecker;
import org.jetbrains.plugins.scala.util.TestUtils;
import org.jetbrains.plugins.scala.util.ScalaToolsFactory;
import org.jetbrains.annotations.NonNls;
import com.intellij.openapi.editor.actionSystem.EditorActionHandler;
import com.intellij.openapi.editor.actionSystem.EditorActionManager;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiElement;
import com.intellij.codeInsight.editorActions.EnterHandler;
import com.intellij.util.IncorrectOperationException;

import java.io.IOException;

import junit.framework.Test;
import junit.framework.Assert;

/**
 * @author ilyas
 */
public class TypeCheckingTest extends ActionTest {

  @NonNls
  private static final String DATA_PATH = "./test/org/jetbrains/plugins/scala/lang/typechecking/data/";

  protected PsiFile myFile;

  public TypeCheckingTest() {
    super(System.getProperty("path") != null ?
        System.getProperty("path") :
        DATA_PATH
    );
  }

  private String processFile(final PsiFile file) throws IncorrectOperationException, InvalidDataException, IOException {
    String result = "";
    String fileText = file.getText();
    int offset = fileText.indexOf(CARET_MARKER);
    fileText = removeMarker(fileText);
    myFile = TestUtils.createPseudoPhysicalFile(project, fileText);
    PsiElement candidate = myFile;
    while (!(candidate instanceof IScalaExpression) &&
        candidate != null) {
      boolean flag = true;
      for (PsiElement child : candidate.getChildren()) {
        if (flag && child.getTextOffset() <= offset && (
            child.getNextSibling() == null || child.getNextSibling().getTextOffset() > offset
        )) {
          candidate = child;
          flag = false;
        }
        if (!flag) break;
      }
    }
    Assert.assertNotNull(candidate);

    IScalaTypeChecker typeChecker = ScalaToolsFactory.getInstance().createScalaTypeChecker();
    result = typeChecker.getTypeRepresentation((IScalaExpression) candidate);

    return result;
  }

  public String transform(String testName, String[] data) throws Exception {
    setSettings();
    String fileText = data[0];
    final PsiFile psiFile = TestUtils.createPseudoPhysicalFile(project, fileText);
    String result = processFile(psiFile);
    System.out.println("------------------------ " + testName + " ------------------------");
    System.out.println(result);
    System.out.println("");
    return result;
  }


  public static Test suite() {
    return new TypeCheckingTest();
  }
}