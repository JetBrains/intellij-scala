package org.jetbrains.plugins.scala.failed.rearranger;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.arrangement.engine.ArrangementEngine;
import com.intellij.util.IncorrectOperationException;
import junit.framework.Test;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.scala.base.ScalaFileSetTestCase;
import org.junit.Assert;
import org.junit.runner.RunWith;
import org.junit.runners.AllTests;

import java.util.Collections;

/**
 * @author Roman.Shein
 * Date: 26.07.13
 */
@RunWith(AllTests.class)
public abstract class RearrangerTest extends ScalaFileSetTestCase {

  public RearrangerTest() {
    super("/rearranger/failedData");
  }

  @NotNull
  @Override
  protected String transform(@NotNull String testName,
                             @NotNull String fileText,
                             @NotNull Project project) {
    final PsiFile file = createLightFile(fileText, project);
    CommandProcessor.getInstance().executeCommand(
            project,
            () -> ApplicationManager.getApplication().runWriteAction(() -> {
              try {
                rearrange(file, project);
              } catch (IncorrectOperationException e) {
                e.printStackTrace();
              }
            }),
            null,
            null
    );
    return file.getText();
  }

  private void rearrange(@NotNull PsiFile file, @NotNull Project project) {
    project.getService(ArrangementEngine.class)
            .arrange(file, Collections.singletonList(file.getTextRange()));

    PsiDocumentManager documentManager = PsiDocumentManager.getInstance(file.getProject());
    Document document = documentManager.getDocument(file);

    Assert.assertNotNull("Wrong PsiFile type provided: the file has no document.", document);
    documentManager.commitDocument(document);
  }

  public static Test suite() {
    return new ScalaFailedRearrangerTest();
  }
}