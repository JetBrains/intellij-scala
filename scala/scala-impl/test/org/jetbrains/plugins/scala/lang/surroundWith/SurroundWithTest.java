package org.jetbrains.plugins.scala.lang.surroundWith;

import com.intellij.codeInsight.generation.surroundWith.SurroundWithHandler;
import com.intellij.lang.surroundWith.Surrounder;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import junit.framework.Test;
import junit.framework.TestCase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.scala.base.ScalaFileSetTestCase;
import org.jetbrains.plugins.scala.lang.surroundWith.descriptors.ScalaSurroundDescriptors$;
import scala.Tuple4;

public class SurroundWithTest extends TestCase {
    public static Test suite() {
        return new ScalaFileSetTestCase("/surroundWith/data/") {
            private void doSurround(final Project project, final PsiFile file,
                                    Surrounder surrounder, int startSelection, int endSelection) {
                FileEditorManager fileEditorManager = FileEditorManager.getInstance(project);
                try {
                    Editor editor = fileEditorManager.openTextEditor(new OpenFileDescriptor(project, file.getVirtualFile(), 0), false);
                    assertNotNull(editor);
                    editor.getSelectionModel().setSelection(startSelection, endSelection);
                    SurroundWithHandler.invoke(project, editor, file, surrounder);
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    fileEditorManager.closeFile(file.getVirtualFile());
                }
            }

            @Override
            @NotNull
            protected String transform(@NotNull String testName,
                                       @NotNull String fileText,
                                       @NotNull Project project) {
                Tuple4<String, Integer, Integer, Integer> res = SurroundWithTestUtil.prepareFile(fileText);
                final PsiFile psiFile = createLightFile(res._1(), project);
                final Surrounder[] surrounder = ScalaSurroundDescriptors$.MODULE$.getSurroundDescriptors()[0].getSurrounders();

                CommandProcessor.getInstance().executeCommand(
                        project,
                        () -> ApplicationManager.getApplication().runWriteAction(
                                () -> doSurround(project, psiFile, surrounder[res._4()], res._2(), res._3())
                        ),
                        null,
                        null
                );

                return psiFile.getText();
            }
        };
    }
}
