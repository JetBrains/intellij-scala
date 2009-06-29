package org.jetbrains.plugins.scala.lang.refactoring.copy;

import com.intellij.codeInsight.ChangeContextUtil;
import com.intellij.featureStatistics.FeatureUsageTracker;
import com.intellij.ide.util.EditorHelper;
import com.intellij.ide.TwoPaneIdeView;
import com.intellij.ide.structureView.StructureViewFactoryEx;
import com.intellij.ide.projectView.ProjectView;
import com.intellij.lang.StdLanguages;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowId;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.jsp.jspJava.JspClass;
import com.intellij.psi.impl.source.jsp.jspJava.JspHolderMethod;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.copy.CopyHandlerDelegate;
import com.intellij.refactoring.copy.CopyHandler;
import com.intellij.util.IncorrectOperationException;
import com.intellij.ui.content.Content;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.scala.ScalaFileType;
import org.jetbrains.plugins.scala.lang.psi.api.base.ScReferenceElement;
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile;
import org.jetbrains.plugins.scala.codeInspection.ScalaRecursiveElementVisitor;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;

public class ScalaCopyClassesHandler implements CopyHandlerDelegate {
  public boolean canCopy(PsiElement[] elements) {
    elements = convertToTopLevelClass(elements);
    for (PsiElement element : elements) {
      if (element instanceof JspClass || element instanceof JspHolderMethod) return false;
    }

    if (elements.length == 1) {
      if (elements[0] instanceof PsiClass && elements[0].getParent() instanceof PsiFile &&
          elements[0].getLanguage() == ScalaFileType.SCALA_LANGUAGE) {
        return true;
      }
    }

    return false;
  }

  private static PsiElement[] convertToTopLevelClass(final PsiElement[] elements) {
    if (elements.length == 1) {
      return new PsiElement[] { getTopLevelClass(elements [0]) };
    }
    return elements;
  }

  public void doCopy(PsiElement[] elements, PsiDirectory defaultTargetDirectory) {
    elements = convertToTopLevelClass(elements);
    Project project = elements [0].getProject();
    FeatureUsageTracker.getInstance().triggerFeatureUsed("refactoring.copyClass");
    PsiClass aClass = (PsiClass)elements[0];
    if (defaultTargetDirectory == null) {
      defaultTargetDirectory = aClass.getContainingFile().getContainingDirectory();
    }
    CopyClassDialog dialog = new CopyClassDialog(aClass, defaultTargetDirectory, project, false);
    dialog.setTitle(RefactoringBundle.message("copy.handler.copy.class"));
    dialog.show();
    if (dialog.isOK()) {
      PsiDirectory targetDirectory = dialog.getTargetDirectory();
      String className = dialog.getClassName();
      copyClassImpl(className, project, aClass, targetDirectory, RefactoringBundle.message("copy.handler.copy.class"), false);
    }
  }

  public void doClone(PsiElement element) {
    element = getTopLevelClass(element);
    FeatureUsageTracker.getInstance().triggerFeatureUsed("refactoring.copyClass");
    PsiClass aClass = (PsiClass)element;
    Project project = element.getProject();

    CopyClassDialog dialog = new CopyClassDialog(aClass, null, project, true);
    dialog.setTitle(RefactoringBundle.message("copy.handler.clone.class"));
    dialog.show();
    if (dialog.isOK()) {
      String className = dialog.getClassName();
      PsiDirectory targetDirectory = element.getContainingFile().getContainingDirectory();
      copyClassImpl(className, project, aClass, targetDirectory, RefactoringBundle.message("copy.handler.clone.class"), true);
    }
  }

  private static void copyClassImpl(final String copyClassName, final Project project, final PsiClass aClass, final PsiDirectory targetDirectory, String commandName, final boolean selectInActivePanel) {
    if (copyClassName == null || copyClassName.length() == 0) return;
    final boolean[] result = new boolean[] {false};
    Runnable command = new Runnable() {
      public void run() {
        final Runnable action = new Runnable() {
          public void run() {
            try {
              PsiElement newElement = doCopyClass(aClass, copyClassName, targetDirectory);
              updateSelectionInActiveProjectView(newElement, project, selectInActivePanel);
              EditorHelper.openInEditor(newElement);

              result[0] = true;
            }
            catch (final IncorrectOperationException ex) {
              ApplicationManager.getApplication().invokeLater(new Runnable() {
                public void run() {
                  Messages.showMessageDialog(project, ex.getMessage(), RefactoringBundle.message("error.title"), Messages.getErrorIcon());
                }
              });
            }
          }
        };
        ApplicationManager.getApplication().runWriteAction(action);
      }
    };
    CommandProcessor processor = CommandProcessor.getInstance();
    processor.executeCommand(project, command, commandName, null);

    if (result[0]) {
      ToolWindowManager.getInstance(project).invokeLater(new Runnable() {
        public void run() {
          ToolWindowManager.getInstance(project).activateEditorComponent();
        }
      });
    }
  }

  public static PsiElement doCopyClass(final PsiClass aClass, final String copyClassName, final PsiDirectory targetDirectory)
      throws IncorrectOperationException {
    PsiElement elementToCopy = aClass.getNavigationElement();
    ChangeContextUtil.encodeContextInfo(elementToCopy, true);
    PsiClass classCopy = (PsiClass)elementToCopy.copy();
    ChangeContextUtil.clearContextInfo(aClass);
    classCopy.setName(copyClassName);
    final String fileName = copyClassName + "." + ScalaFileType.SCALA_FILE_TYPE.getDefaultExtension();
    final PsiFile createdFile = targetDirectory.copyFileFrom(fileName, elementToCopy.getContainingFile());
    PsiElement newElement = createdFile;
    if (createdFile instanceof PsiClassOwner) {
      final PsiClass[] classes = ((PsiClassOwner)createdFile).getClasses();
      assert classes.length > 0 : createdFile.getText();
      for (PsiClass clazz : classes) {
        createdFile.deleteChildRange(clazz, clazz);
      }
      PsiClass newClass = (PsiClass)createdFile.add(classCopy);
      ((ScalaFile) createdFile).addImportForClass(aClass);
      ChangeContextUtil.decodeContextInfo(newClass, newClass, null);
      replaceClassOccurrences(newClass, (PsiClass) elementToCopy);
      newElement = newClass;
    }
    return newElement;
  }

  private static void replaceClassOccurrences(final PsiClass newClass, final PsiClass oldClass) throws IncorrectOperationException {
    final List<ScReferenceElement> selfReferences = new ArrayList<ScReferenceElement>();
    newClass.accept(new ScalaRecursiveElementVisitor() {
      public void visitReference(ScReferenceElement reference) {
        super.visitReference(reference);
        final PsiElement target = reference.resolve();
        if (target == null) return;
        if (target == oldClass || target.getNavigationElement() == oldClass) {
          selfReferences.add(reference);
        }
      }
    });
    for (ScReferenceElement selfReference : selfReferences) {
      selfReference.bindToElement(newClass);
    }
  }

  @Nullable
  private static PsiClass getTopLevelClass(PsiElement element) {
    while (true) {
      if (element == null || element instanceof PsiFile) break;
      if (element instanceof PsiClass && element.getParent() instanceof PsiFile) break;
      element = element.getParent();
    }
    if (element instanceof PsiClassOwner) {
      PsiClass[] classes = ((PsiClassOwner)element).getClasses();
      if (classes.length > 0) {
        element = classes[0];
      }
    }
    return element instanceof PsiClass ? (PsiClass)element : null;
  }

  static void updateSelectionInActiveProjectView(PsiElement newElement, Project project, boolean selectInActivePanel) {
    String id = ToolWindowManager.getInstance(project).getActiveToolWindowId();
    if (id != null) {
      ToolWindow window = ToolWindowManager.getInstance(project).getToolWindow(id);
      Content selectedContent = window.getContentManager().getSelectedContent();
      if (selectedContent != null) {
        JComponent component = selectedContent.getComponent();
        if (component instanceof TwoPaneIdeView) {
          ((TwoPaneIdeView) component).selectElement(newElement, selectInActivePanel);
          return;
        }
      }
    }
    if (ToolWindowId.PROJECT_VIEW.equals(id)) {
      ProjectView.getInstance(project).selectPsiElement(newElement, true);
    }
    else if (ToolWindowId.STRUCTURE_VIEW.equals(id)) {
      VirtualFile virtualFile = newElement.getContainingFile().getVirtualFile();
      FileEditor editor = FileEditorManager.getInstance(newElement.getProject()).getSelectedEditor(virtualFile);
      StructureViewFactoryEx.getInstanceEx(project).getStructureViewWrapper().selectCurrentElement(editor, true);
    }
  }
}
