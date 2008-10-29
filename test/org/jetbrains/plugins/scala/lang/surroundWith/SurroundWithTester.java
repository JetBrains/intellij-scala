/*
 * Copyright 2000-2008 JetBrains s.r.o.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jetbrains.plugins.scala.lang.surroundWith;

import com.intellij.codeInsight.generation.surroundWith.SurroundWithHandler;
import com.intellij.lang.surroundWith.Surrounder;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.util.IncorrectOperationException;
import junit.framework.Test;
import org.jetbrains.plugins.scala.testcases.BaseScalaFileSetTestCase;
import org.jetbrains.plugins.scala.util.ScalaToolsFactory;
import org.jetbrains.plugins.scala.util.TestUtils;

/**
 * @autor: Dmitry.Krasilschikov
 * @date: 22.01.2007
 */
public class SurroundWithTester extends BaseScalaFileSetTestCase {
  private static final String DATA_PATH = "test/org/jetbrains/plugins/scala/lang/surroundWith/data/";

  protected String getDataPath() {
    return "test/org/jetbrains/plugins/scala/lang/surroundWith/data/";
  }

//  static String getDataPath() {
//    return "test/org/jetbrains/plugins/scala/lang/surroundWith/data/";
//  }
//  private Surrounder surrounder;

  public static Test suite() {
    return new SurroundWithTester(DATA_PATH);
  }

  public SurroundWithTester(String datePath) {
    super(datePath);
  }

  protected void selectContentInTemplateBody(PsiFile file, Editor editor) {
    PsiElement classDef = file.getFirstChild();
    PsiElement topDefTmpl = classDef.getLastChild();
    PsiElement templateBody = topDefTmpl.getFirstChild();

    PsiElement expression = templateBody.getChildren()[0];

    editor.getSelectionModel().setSelection(templateBody.getTextRange().getStartOffset()+1, templateBody.getTextRange().getEndOffset()-1);
  }

  protected void doSurround(final Project project, final PsiFile file, Surrounder surrounder) throws IncorrectOperationException {
//    Editor editor = FileEditorManager.getInstance(project).getSelectedTextEditor();

    PsiFile myFile;
    FileEditorManager fileEditorManager;
    Editor editor;
    myFile = TestUtils.createPseudoPhysicalScalaFile(project, file.getText());
    fileEditorManager = FileEditorManager.getInstance(project);

    try {
      editor = fileEditorManager.openTextEditor(new OpenFileDescriptor(project, myFile.getVirtualFile(), 0), false);

      selectContentInTemplateBody(file, editor);
      SurroundWithHandler.invoke(project, editor, file, surrounder);
    } catch (Exception e){
      e.printStackTrace();
    } finally{

    fileEditorManager.closeFile(myFile.getVirtualFile());
    editor = null;
  }
  }

  public String transform(String testName, String[] data) throws Exception {
    final int surroundType = Integer.parseInt(data[0].substring(0,2).trim());
    String fileText = data[0].substring(2).trim();
    final PsiFile psiFile = TestUtils.createPseudoPhysicalScalaFile(myProject, fileText);

    final Surrounder[] surrounder = surrounder();
//    for (final Surrounder surrounder : surrounders) {
    CommandProcessor.getInstance().executeCommand(myProject, new Runnable() {
      public void run() {
        ApplicationManager.getApplication().runWriteAction(new Runnable() {
          public void run() {
            try {
              doSurround(myProject, psiFile, surrounder[surroundType]);
            } catch (IncorrectOperationException e) {
              e.printStackTrace();
            }
          }
        });
      }
    }, null, null);

//    }

    System.out.println("------------------------ " + testName + " ------------------------");
    System.out.println(psiFile.getText().toString());
    System.out.println("");
    return psiFile.getText();
  }

  public Surrounder[] surrounder() {
    return ScalaToolsFactory.getInstance().createSurroundDescriptors().getSurroundDescriptors()[0].getSurrounders();
  }
}