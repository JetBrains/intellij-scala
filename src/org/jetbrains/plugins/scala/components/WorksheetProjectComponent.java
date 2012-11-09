package org.jetbrains.plugins.scala.components;

import com.intellij.execution.process.ProcessHandler;
import com.intellij.icons.AllIcons;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.impl.ActionButton;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.AbstractProjectComponent;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.FileEditorManagerAdapter;
import com.intellij.openapi.fileEditor.FileEditorManagerListener;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.impl.PsiManagerEx;
import com.intellij.uiDesigner.lw.LwHSpacer;
import org.jetbrains.plugins.scala.ScalaBundle;
import org.jetbrains.plugins.scala.ScalaFileType;
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes;
import org.jetbrains.plugins.scala.worksheet.WorksheetFoldingBuilder$;
import org.jetbrains.plugins.scala.worksheet.actions.RunWorksheetAction;
import org.jetbrains.plugins.scala.worksheet.actions.WorksheetInfo$;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * @author Ksenia.Sautina
 * @since 10/30/12
 */

public class WorksheetProjectComponent extends AbstractProjectComponent {
  private final FileEditorManager myFileEditorManager;

  public WorksheetProjectComponent(final Project project, FileEditorManager fileEditorManager) {
    super(project);
    myFileEditorManager = fileEditorManager;
    project.getMessageBus().connect(project).subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, new FileEditorManagerAdapter() {
      @Override
      public void fileOpened(FileEditorManager source, VirtualFile file) {
        updateNotifications(file);
      }
    });
  }

  public void updateNotifications(final VirtualFile file) {
    if (!file.getExtension().equals(ScalaFileType.WORKSHEET_EXTENSION)) return;
    ApplicationManager.getApplication().runReadAction(new Runnable() {
      public void run() {
        if (myProject.isDisposed()) return;
        FileEditor[] editors = myFileEditorManager.getAllEditors(file);
        for (FileEditor editor : editors) {
          //todo action
          JPanel panel = new JPanel();
          panel.setLayout(new BorderLayout());

          RunWorksheetAction executeAction = new RunWorksheetAction();
          ActionButton executeButton = new ActionButton(executeAction, executeAction.getTemplatePresentation(), executeAction.getTemplatePresentation().getText(), ActionToolbar.DEFAULT_MINIMUM_BUTTON_SIZE);
          executeButton.getAction().getTemplatePresentation().setIcon(AllIcons.Actions.Execute);
          executeButton.setToolTipText(ScalaBundle.message("worksheet.execute.button"));
//          panel.add(executeButton);

          AnAction cleanAction = new AnAction() {
            @Override
            public void actionPerformed(AnActionEvent e) {
              DataContext dataContext = e.getDataContext();
              Editor editor = PlatformDataKeys.EDITOR.getData(dataContext);
              Project project = PlatformDataKeys.PROJECT.getData(dataContext);
              assert project != null;
              PsiFile psiFile = ((PsiManagerEx)PsiManager.getInstance(project)).getFileManager().getCachedPsiFile(file);
              assert psiFile != null;
              assert editor != null;
              cleanWorksheet(psiFile.getNode(), editor, project);
            }
          };

          ActionButton cleanButton = new ActionButton(cleanAction, cleanAction.getTemplatePresentation(),
            cleanAction.getTemplatePresentation().getText(), ActionToolbar.DEFAULT_MINIMUM_BUTTON_SIZE);
          cleanButton.getAction().getTemplatePresentation().setIcon(AllIcons.Actions.GC);
          cleanButton.setToolTipText(ScalaBundle.message("worksheet.clear.button"));
//          panel.add(cleanButton);

          myFileEditorManager.addTopComponent(editor, panel);
        }
      }
    });
  }

  private void cleanWorksheet(final ASTNode node, final Editor editor, final Project project) {
    ApplicationManager.getApplication().invokeLater(new Runnable() {
      @Override
      public void run() {
        ApplicationManager.getApplication().runWriteAction(new Runnable() {
          @Override
          public void run() {
            Document document = editor.getDocument();
            if ((node.getPsi() instanceof PsiComment) &&
              (node.getText().startsWith(WorksheetFoldingBuilder$.MODULE$.FIRST_LINE_PREFIX()) ||
                node.getText().startsWith(WorksheetFoldingBuilder$.MODULE$.LINE_PREFIX()))) {
              int line = document.getLineNumber(node.getPsi().getTextRange().getStartOffset());
              int startOffset = document.getLineStartOffset(line);
              String beginningOfTheLine = document.getText(new TextRange(startOffset, node.getPsi().getTextRange().getStartOffset()));
              if (beginningOfTheLine.trim().equals("")) document.deleteString(startOffset, node.getPsi().getTextRange().getEndOffset() + 1);
              else document.deleteString(node.getPsi().getTextRange().getStartOffset(), node.getPsi().getTextRange().getEndOffset());
              PsiDocumentManager.getInstance(project).commitDocument(document);
            }
            List<ASTNode> list = new ArrayList<ASTNode>(Arrays.asList(node.getChildren(null)));
            for (ASTNode child : list) {
              cleanWorksheet(child, editor, project);
            }
          }
        });
      }
    });
  }
}