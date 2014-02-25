package org.jetbrains.plugins.scala.components;

import com.intellij.openapi.components.AbstractProjectComponent;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.project.ProjectManagerAdapter;
import org.jetbrains.plugins.scala.worksheet.runconfiguration.WorksheetViewerInfo;

/**
 * @author Ksenia.Sautina
 * @since 10/30/12
 */

public class WorksheetProjectComponent extends AbstractProjectComponent {
  public WorksheetProjectComponent(final Project project, FileEditorManager fileEditorManager) {     //todo move all the ... in WorksheetFileHook
    super(project);
    project.getMessageBus().connect(project).subscribe(ProjectManager.TOPIC, new ProjectManagerAdapter() {
      @Override
      public void projectClosed(Project project) {
        Editor editor = FileEditorManager.getInstance(project).getSelectedTextEditor();
        Editor viewer = WorksheetViewerInfo.getViewer(editor);
        if (viewer != null) {
          EditorFactory.getInstance().releaseEditor(viewer);
        }
      }
    });
  }
}