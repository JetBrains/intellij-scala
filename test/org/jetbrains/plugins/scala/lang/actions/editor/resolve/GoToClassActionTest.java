/*
*/
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
/*

package org.jetbrains.plugins.scala.lang.actions.editor.resolve;

import org.jetbrains.plugins.scala.lang.actions.ActionTest;
import org.jetbrains.plugins.scala.util.TestUtils;
import org.jetbrains.plugins.scala.components.ScalaComponents;
import org.jetbrains.annotations.NonNls;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.actionSystem.EditorActionHandler;
import com.intellij.openapi.editor.actionSystem.EditorActionManager;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiElement;
import com.intellij.util.IncorrectOperationException;
import com.intellij.codeInsight.editorActions.EnterHandler;
import com.intellij.codeInsight.navigation.CtrlMouseHandler;
import com.intellij.codeInsight.TargetElementUtil;

import java.io.IOException;

import junit.framework.Test;

*/
/**
 * @author Ilya.Sergey
 */
/*
public class GoToClassActionTest extends ActionTest {

  @NonNls
  private static final String DATA_PATH = "./test/org/jetbrains/plugins/scala/lang/actions/editor/resolve/data/class";

  protected Editor myEditor;
  protected FileEditorManager fileEditorManager;
  protected String newDocumentText;
  protected PsiFile myFile;

  public GoToClassActionTest() {
    super(System.getProperty("path") != null ?
            System.getProperty("path") :
            DATA_PATH
    );
  }

  private String processFile(final PsiFile file) throws IncorrectOperationException, InvalidDataException, IOException {

    String result = "nothing";
    String fileText = file.getText();
    int offset = fileText.indexOf(CARET_MARKER);
    fileText = removeMarker(fileText);
    try {
      myFile = TestUtils.createPseudoPhysicalFile(project, fileText, 1);
      fileEditorManager = FileEditorManager.getInstance(project);
      myEditor = fileEditorManager.openTextEditor(new OpenFileDescriptor(project, myFile.getVirtualFile(), 0), false);
      myEditor.getCaretModel().moveToOffset(offset);

      ScalaModuleCachesManager cManager = (ScalaModuleCachesManager)ModuleManager.getInstance(project).getModules()[0].getComponent(ScalaComponents.SCALA_CACHE_MANAGER);
      cManager.getModuleFilesCache().processFileChanged(myFile.getVirtualFile());
      cManager.getModuleFilesCache().refresh();

      PsiReference ref = TargetElementUtil.findReference(myEditor);
      if (ref != null) {
        PsiElement elem = ref.resolve();
        PsiFile toFile = elem.getContainingFile();
        Editor toEditor = fileEditorManager.openTextEditor(new OpenFileDescriptor(project, toFile.getVirtualFile(), 0), false);
        toEditor.getCaretModel().moveToOffset(elem.getTextOffset());
        result = toEditor.getDocument().getText();
        result = result.substring(0, offset) + CARET_MARKER + result.substring(offset);
      }
    }

    finally {
      fileEditorManager.closeFile(myFile.getVirtualFile());
      myEditor = null;
    }

    System.out.println(result);

    return result;
  }

  public String transform
          (String
                  testName, String[] data) throws Exception {
    String fileText = data[0];
    final PsiFile psiFile = TestUtils.createPseudoPhysicalFile(project, fileText);
    return processFile(psiFile);
  }


  public static Test suite
          () {
    return new GoToClassActionTest();
  }
}
*/
