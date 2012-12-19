package org.jetbrains.plugins.scala.components;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.impl.ActionButton;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.AbstractProjectComponent;
import com.intellij.openapi.fileEditor.*;
import com.intellij.openapi.keymap.KeymapManager;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.plugins.scala.ScalaBundle;
import org.jetbrains.plugins.scala.ScalaFileType;
import org.jetbrains.plugins.scala.worksheet.actions.CleanWorksheetAction;
import org.jetbrains.plugins.scala.worksheet.actions.CopyWorksheetAction;
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
          JPanel panel = new JPanel();
          panel.setLayout(new FlowLayout(FlowLayout.LEFT));

          RunWorksheetAction executeAction = new RunWorksheetAction();
          Presentation executePresentation = executeAction.getTemplatePresentation();
          Shortcut[] shortcuts = KeymapManager.getInstance().getActiveKeymap().getShortcuts("Scala.RunWorksheet");
          if (shortcuts.length > 0) {
            String shortcutText = " (" + KeymapUtil.getShortcutText(shortcuts[0]) + ")";
            executePresentation.setText(ScalaBundle.message("worksheet.execute.button") + shortcutText);
          }
          ActionButton executeButton = new ActionButton(executeAction, executePresentation,
            ActionPlaces.EDITOR_TOOLBAR, ActionToolbar.DEFAULT_MINIMUM_BUTTON_SIZE);
          executeButton.getAction().getTemplatePresentation().setIcon(AllIcons.Actions.Execute);
          panel.add(executeButton);

          CleanWorksheetAction cleanAction = new CleanWorksheetAction();
          Presentation cleanPresentation = cleanAction.getTemplatePresentation();
          cleanPresentation.setText(ScalaBundle.message("worksheet.clear.button"));
          ActionButton cleanButton = new ActionButton(cleanAction, cleanPresentation,
          ActionPlaces.EDITOR_TOOLBAR, ActionToolbar.DEFAULT_MINIMUM_BUTTON_SIZE);
          cleanButton.setToolTipText(ScalaBundle.message("worksheet.clear.button"));
          cleanButton.getAction().getTemplatePresentation().setIcon(AllIcons.Actions.GC);
          panel.add(cleanButton);

          CopyWorksheetAction copyAction = new CopyWorksheetAction();
          Presentation copyPresentation = copyAction.getTemplatePresentation();
          copyPresentation.setText(ScalaBundle.message("worksheet.copy.button"));
          ActionButton copyButton = new ActionButton(copyAction, copyPresentation,
            ActionPlaces.EDITOR_TOOLBAR, ActionToolbar.DEFAULT_MINIMUM_BUTTON_SIZE);
          copyButton.setToolTipText(ScalaBundle.message("worksheet.copy.button"));
          copyButton.getAction().getTemplatePresentation().setIcon(AllIcons.Actions.Copy);
          panel.add(copyButton);

          myFileEditorManager.addTopComponent(editor, panel);
        }
      }
    });
  }
}