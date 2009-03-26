package org.jetbrains.plugins.scala.refactoring.rename;

import com.intellij.codeInsight.TargetElementUtilBase;
import com.intellij.lang.LanguageNamesValidation;
import com.intellij.lang.refactoring.NamesValidator;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.psi.*;
import com.intellij.refactoring.rename.RenameProcessor;

import java.io.File;
import java.io.IOException;

import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveTestCase;
import org.jetbrains.plugins.scala.util.TestUtils;

/**
 * @author ilyas
 */
public class ScalaRenameTest extends ScalaResolveTestCase {

  protected String getTestDataPath() {
    return TestUtils.getTestDataPath() + "/rename/";
  }

  protected PsiElement getTargetElement(String str) throws Exception {
    PsiReference ref = configureByFile(str);
    return ref == null ? null : ref.resolve();
  }

  public void testSimpleClass() throws Exception {
    final PsiElement element = getTargetElement("class/circle.scala");
    performAction(element, "newName");
    checkResultByFile(element, "class/circle.test");
  }

  private void checkResultByFile(PsiElement element, String s) throws IOException {
    final PsiFile file = element.getContainingFile();
    final VirtualFile vFile = LocalFileSystem.getInstance().findFileByPath(getTestDataPath() + s.replace(File.separatorChar, '/'));
    assertEquals(file.getText(), VfsUtil.loadText(vFile));
  }

  private void performAction(final PsiElement element, final String newName) {

    CommandProcessor.getInstance().executeCommand(myProject, new Runnable() {
      public void run() {
        ApplicationManager.getApplication().runWriteAction(new Runnable() {
          public void run() {
            new RenameProcessor(myProject, element, newName, false, false).run();
          }
        });
      }
    }, "Rename", null);
  }

}