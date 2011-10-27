/*
 * Copyright 2000-2008 JetBrains s.r.o.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jetbrains.plugins.scala.components;

import com.intellij.ide.projectView.PresentationData;
import com.intellij.ide.projectView.TreeStructureProvider;
import com.intellij.ide.projectView.ViewSettings;
import com.intellij.ide.projectView.impl.nodes.ClassTreeNode;
import com.intellij.ide.projectView.impl.nodes.PsiFileNode;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassOwner;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.SyntheticElement;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile;
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTypeDefinition;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * @author ven
 */
public class ScalaDefsProjectViewProvider implements TreeStructureProvider {
  private static boolean hasNameOfFile(ScTypeDefinition type) {
    ScalaFile scalaFile = getFile(type);
    VirtualFile virtualFile = scalaFile == null ? null : scalaFile.getVirtualFile();
    if (virtualFile == null) {
      return true;
    } else {
      String fileName = virtualFile.getNameWithoutExtension();
      String className = type.getName();
      return fileName.equals(className);
    }
  }

  private static ScalaFile getFile(ScTypeDefinition type) {
    return (ScalaFile) type.getContainingFile();
  }

  public Collection<AbstractTreeNode> modify(AbstractTreeNode parent, Collection<AbstractTreeNode> children, ViewSettings settings) {
    List<AbstractTreeNode> result = new ArrayList<AbstractTreeNode>();
    
    for (AbstractTreeNode child : children) {
      Object childValue = child.getValue();
      Object parentValue = parent.getValue();

      boolean insertFile = childValue instanceof ScTypeDefinition
          && parentValue instanceof PsiDirectory
          && !hasNameOfFile((ScTypeDefinition) childValue);

      result.add(insertFile
          ? new MyClassOwnerTreeNode(getFile((ScTypeDefinition) childValue), settings)
          : child instanceof ClassTreeNode
          ? new MyClassOwnerTreeNodeDecorator((ClassTreeNode) child)
          : child);
    }
    
    return result;
  }

  @Nullable
  public Object getData(Collection<AbstractTreeNode> selected, String dataName) {
    return null;
  }

  private static class MyClassOwnerTreeNodeDecorator extends ClassTreeNode {
    public MyClassOwnerTreeNodeDecorator(ClassTreeNode delegate) {
      super(delegate.getProject(), delegate.getValue(), delegate.getSettings());
    }

    @Override
    public String getTitle() {
      PsiClass value = getValue();
      return value != null && value.isValid() ? value.getQualifiedName() : null;
    }
  }

  private static class MyClassOwnerTreeNode extends PsiFileNode {
    public MyClassOwnerTreeNode(PsiClassOwner classOwner, ViewSettings settings) {
      super(classOwner.getProject(), classOwner, settings);
    }

    @Override
    public Collection<AbstractTreeNode> getChildrenImpl() {
      final ViewSettings settings = getSettings();
      final ArrayList<AbstractTreeNode> result = new ArrayList<AbstractTreeNode>();
      for (PsiClass aClass : ((PsiClassOwner) getValue()).getClasses()) {
        if (!(aClass instanceof SyntheticElement)) {
          result.add(new ClassTreeNode(myProject, aClass, settings));
        }
      }
      return result;
    }
   
    protected void updateImpl(PresentationData data) {
      super.updateImpl(data);
      data.setPresentableText(getValue().getName());
      data.setIcons(getValue().getViewProvider().getVirtualFile().getIcon());
    }
  }
}