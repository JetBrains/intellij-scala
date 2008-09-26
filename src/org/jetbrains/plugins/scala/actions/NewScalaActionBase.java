package org.jetbrains.plugins.scala.actions;

import com.intellij.CommonBundle;
import com.intellij.ide.IdeView;
import com.intellij.ide.actions.CreateElementActionBase;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.DataKeys;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.ui.Messages;
import com.intellij.psi.*;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.scala.util.ScalaUtils;

import javax.swing.*;

/**
 * @author ilyas
 */
public abstract class NewScalaActionBase extends CreateElementActionBase {

  @NonNls
  private static final String SCALA_EXTENSIOIN = ".scala";

  public NewScalaActionBase(String text, String description, Icon icon) {
    super(text, description, icon);
  }

  @NotNull
  protected final PsiElement[] invokeDialog(final Project project, final PsiDirectory directory) {
    MyInputValidator validator = new MyInputValidator(project, directory);
    Messages.showInputDialog(project, getDialogPrompt(), getDialogTitle(), Messages.getQuestionIcon(), "", validator);

    return validator.getCreatedElements();
  }

  protected abstract String getDialogPrompt();

  protected abstract String getDialogTitle();

  public void update(final AnActionEvent event) {
    super.update(event);
    final Presentation presentation = event.getPresentation();
    final DataContext context = event.getDataContext();
    Module module = (Module) context.getData(DataKeys.MODULE.getName());

    if (!ScalaUtils.isSuitableModule(module) ||
        !presentation.isEnabled() ||
        !isUnderSourceRoots(event) ||
        !ScalaActionUtil.isScalaConfigured(event)) {
      presentation.setEnabled(false);
      presentation.setVisible(false);
    } else {
      presentation.setEnabled(true);
      presentation.setVisible(true);
    }

  }

  public static boolean isUnderSourceRoots(final AnActionEvent e) {
    final DataContext context = e.getDataContext();
    Module module = (Module) context.getData(DataKeys.MODULE.getName());
    if (!ScalaUtils.isSuitableModule(module)) {
      return false;
    }
    final IdeView view = (IdeView) context.getData(DataKeys.IDE_VIEW.getName());
    final Project project = (Project) context.getData(DataKeys.PROJECT.getName());
    if (view != null && project != null) {
      ProjectFileIndex projectFileIndex = ProjectRootManager.getInstance(project).getFileIndex();
      PsiDirectory[] dirs = view.getDirectories();
      for (PsiDirectory dir : dirs) {
        PsiPackage aPackage = JavaDirectoryService.getInstance().getPackage(dir);
        if (projectFileIndex.isInSourceContent(dir.getVirtualFile()) && aPackage != null) {
          return true;
        }
      }
    }

    return false;
  }

  @NotNull
  protected PsiElement[] create(String newName, PsiDirectory directory) throws Exception {
    return doCreate(newName, directory);
  }

  @NotNull
  protected abstract PsiElement[] doCreate(String newName, PsiDirectory directory) throws Exception;

  protected static PsiFile createClassFromTemplate(final PsiDirectory directory, String className, @NonNls String templateName,
                                                   @NonNls String... parameters) throws IncorrectOperationException {
    return ScalaTemplatesFactory.createFromTemplate(directory, className, className + SCALA_EXTENSIOIN, templateName, parameters);
  }


  protected String getErrorTitle() {
    return CommonBundle.getErrorTitle();
  }

  protected void checkBeforeCreate(String newName, PsiDirectory directory) throws IncorrectOperationException {
    JavaDirectoryService.getInstance().checkCreateClass(directory, newName);
  }
}

