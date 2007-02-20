package org.jetbrains.plugins.scala.components;

import com.intellij.ide.projectView.TreeStructureProvider;
import com.intellij.ide.projectView.ViewSettings;
import com.intellij.ide.projectView.ProjectViewNode;
import com.intellij.ide.projectView.PresentationData;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiClass;
import com.intellij.lang.StdLanguages;

import java.util.Collection;
import java.util.List;
import java.util.ArrayList;
import java.util.Collections;

import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.scala.ScalaFileType;

/**
 * @author ven
 */
public class ScalaDefsProjectViewProvider implements TreeStructureProvider, ProjectComponent {
  private class Node extends ProjectViewNode<PsiClass> {
    public Node(PsiClass aClass, ViewSettings settings) {
      super(aClass.getProject(), aClass, settings);
    }

    public void navigate(boolean requestFocus) {
      getValue().navigate(requestFocus);
    }

    public boolean canNavigate() {
      return true;
    }

    public boolean contains(@NotNull VirtualFile file) {
      return file.equals(getValue().getContainingFile().getVirtualFile());
    }

    @NotNull
    public Collection<? extends AbstractTreeNode> getChildren() {
      return Collections.emptyList();
    }

    protected void update(PresentationData presentation) {
      PsiClass aClass = getValue();
      presentation.setPresentableText(aClass.getName());
      presentation.setIcons(aClass.getIcon(0));
    }
  }

  public Collection<AbstractTreeNode> modify(AbstractTreeNode parent, Collection<AbstractTreeNode> children, ViewSettings settings) {
    List<AbstractTreeNode>result = new ArrayList<AbstractTreeNode>();
    for (final AbstractTreeNode child : children) {
      Object value = child.getValue();
      if (value instanceof PsiFile && ((PsiFile) value).getLanguage().equals(ScalaFileType.SCALA_FILE_TYPE.getLanguage())) {
        PsiJavaFile javaPsi = (PsiJavaFile) ((PsiFile) value).getViewProvider().getPsi(StdLanguages.JAVA);
        PsiClass[] classes = javaPsi.getClasses();
        if (classes.length > 0) {
          for (final PsiClass aClass : classes) {
            result.add(new Node(aClass, settings));
          }
        } else {
          result.add(child);
        }
      } else {
        result.add(child);
      }
    }

    return result;
  }

  @Nullable
  public Object getData(Collection<AbstractTreeNode> selected, String dataName) {
    return null;
  }

  public void projectOpened() {}

  public void projectClosed() {}

  @NonNls
  @NotNull
  public String getComponentName() {
    return "ScalaDefsProjectViewProvider";
  }

  public void initComponent() {}

  public void disposeComponent() {}
}
