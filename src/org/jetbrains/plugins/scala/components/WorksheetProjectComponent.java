package org.jetbrains.plugins.scala.components;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.ActionToolbar;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.impl.ActionButton;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.AbstractProjectComponent;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.FileEditorManagerAdapter;
import com.intellij.openapi.fileEditor.FileEditorManagerListener;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.plugins.scala.ScalaBundle;
import org.jetbrains.plugins.scala.ScalaFileType;
import org.jetbrains.plugins.scala.worksheet.actions.CleanWorksheetAction;
import org.jetbrains.plugins.scala.worksheet.actions.RunWorksheetAction;

import javax.swing.*;
import java.awt.*;

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
    if (!ScalaFileType.WORKSHEET_EXTENSION.equals(file.getExtension())) return;
    ApplicationManager.getApplication().runReadAction(new Runnable() {
      public void run() {
        if (myProject.isDisposed()) return;
        FileEditor[] editors = myFileEditorManager.getAllEditors(file);
        for (FileEditor editor : editors) {
          //todo action
          JPanel panel = new JPanel();
          panel.setLayout(new FlowLayout(FlowLayout.LEFT));

          RunWorksheetAction executeAction = new RunWorksheetAction();
          ActionButton executeButton = new ActionButton(executeAction, executeAction.getTemplatePresentation(),
            ActionPlaces.EDITOR_TOOLBAR, ActionToolbar.DEFAULT_MINIMUM_BUTTON_SIZE);
          executeButton.setToolTipText(ScalaBundle.message("worksheet.execute.button"));
          executeButton.getAction().getTemplatePresentation().setIcon(AllIcons.Actions.Execute);
          panel.add(executeButton);

          AnAction cleanAction = new CleanWorksheetAction(file);
          ActionButton cleanButton = new ActionButton(cleanAction, cleanAction.getTemplatePresentation(),
            ActionPlaces.EDITOR_TOOLBAR, ActionToolbar.DEFAULT_MINIMUM_BUTTON_SIZE);
          cleanButton.setToolTipText(ScalaBundle.message("worksheet.clear.button"));
          cleanButton.getAction().getTemplatePresentation().setIcon(AllIcons.Actions.GC);
          panel.add(cleanButton);

          myFileEditorManager.addTopComponent(editor, panel);
        }
      }
    });
  }
}