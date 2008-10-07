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

package org.jetbrains.plugins.scala.lang.actions.editor.enter;

import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.actionSystem.EditorActionHandler;
import com.intellij.openapi.editor.actionSystem.EditorActionManager;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.psi.PsiFile;
import com.intellij.util.IncorrectOperationException;
import junit.framework.Test;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.plugins.scala.lang.actions.ActionTestBase;
import org.jetbrains.plugins.scala.util.TestUtils;
import org.jetbrains.plugins.scala.ScalaFileType;

import java.io.IOException;


public class EnterActionTest extends ActionTestBase {

  @NonNls
  private static final String DATA_PATH = "./test/org/jetbrains/plugins/scala/lang/actions/editor/enter/data/";

  protected Editor myEditor;
  protected FileEditorManager fileEditorManager;
  protected String newDocumentText;
  protected PsiFile myFile;

  public EnterActionTest() {
    super(System.getProperty("path") != null ?
            System.getProperty("path") :
            DATA_PATH
    );
  }

  @Override
  protected void setSettings() {
    super.setSettings();
    mySettings = getSettings();
    mySettings.getIndentOptions(ScalaFileType.SCALA_FILE_TYPE).INDENT_SIZE = 2;
    mySettings.getIndentOptions(ScalaFileType.SCALA_FILE_TYPE).CONTINUATION_INDENT_SIZE = 2;
    mySettings.getIndentOptions(ScalaFileType.SCALA_FILE_TYPE).TAB_SIZE = 2;
  }

  protected EditorActionHandler getMyHandler() {
    return EditorActionManager.getInstance().getActionHandler(IdeActions.ACTION_EDITOR_ENTER);
  }


  private String processFile(final PsiFile file) throws IncorrectOperationException, InvalidDataException, IOException {
    String result;
    String fileText = file.getText();
    int offset = fileText.indexOf(CARET_MARKER);
    fileText = removeMarker(fileText);
    myFile = TestUtils.createPseudoPhysicalScalaFile(myProject, fileText);
    fileEditorManager = FileEditorManager.getInstance(myProject);
    myEditor = fileEditorManager.openTextEditor(new OpenFileDescriptor(myProject, myFile.getVirtualFile(), 0), false);
    myEditor.getCaretModel().moveToOffset(offset);

    final myDataContext dataContext = getDataContext(myFile);
    final EditorActionHandler handler = getMyHandler();

    try {
      performAction(myProject, new Runnable() {
        public void run() {
          handler.execute(myEditor, dataContext);
        }
      });

      offset = myEditor.getCaretModel().getOffset();
      result = myEditor.getDocument().getText();
      result = result.substring(0, offset) + CARET_MARKER + result.substring(offset);
    } finally {
      fileEditorManager.closeFile(myFile.getVirtualFile());
      myEditor = null;
    }

    return result;
  }

  public String transform(String testName, String[] data) throws Exception {
    setSettings();
    String fileText = data[0];
    final PsiFile psiFile = TestUtils.createPseudoPhysicalScalaFile(myProject, fileText);
    String result = processFile(psiFile);
    System.out.println("------------------------ " + testName + " ------------------------");
    System.out.println(result);
    System.out.println("");
    return result;
  }


  public static Test suite() {
    return new EnterActionTest();
  }
}
