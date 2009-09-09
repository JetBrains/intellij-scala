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
import com.intellij.ide.projectView.ProjectViewNode;
import com.intellij.ide.projectView.TreeStructureProvider;
import com.intellij.ide.projectView.ViewSettings;
import com.intellij.ide.projectView.impl.nodes.ClassTreeNode;
import com.intellij.ide.projectView.impl.nodes.ProjectViewProjectNode;
import com.intellij.ide.projectView.impl.nodes.PsiFileNode;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.scala.icons.Icons;
import org.jetbrains.plugins.scala.lang.formatting.settings.ScalaCodeStyleSettings;
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile;
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTypeDefinition;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * @author ven
 */
public class ScalaDefsProjectViewProvider implements TreeStructureProvider, ProjectComponent {
  private Project myProject;

  public ScalaDefsProjectViewProvider(Project project) {
    myProject = project;
  }

  public void initComponent() {
  }

  public void disposeComponent() {
  }

  @NotNull
  public String getComponentName() {
    return "ScalaTreeStructureProvider";
  }

  public void projectOpened() {
  }

  public void projectClosed() {
  }

  public Collection<AbstractTreeNode> modify(AbstractTreeNode parent, Collection<AbstractTreeNode> children, ViewSettings settings) {
    List<AbstractTreeNode> result = new ArrayList<AbstractTreeNode>();

    for (final AbstractTreeNode child : children) {
      ProjectViewNode treeNode = (ProjectViewNode) child;
      Object o = treeNode.getValue();
      if (o instanceof ScalaFile) {
        final ScalaFile scalaFile = (ScalaFile) o;

        final ViewSettings viewSettings = ((ProjectViewNode) parent).getSettings();
        ScalaCodeStyleSettings styleSettings = CodeStyleSettingsManager.getSettings(myProject).getCustomSettings(ScalaCodeStyleSettings.class);
        
        if (styleSettings.SHOW_FILES_IN_PROJECT_VIEW && getUltimateParent(parent) instanceof ProjectViewProjectNode) {
          showTypesIfSimpleFileOtherwiseShowFile(settings, result, scalaFile, viewSettings);
        } else {
          if (scalaFile.typeDefinitions().length == 0 || scalaFile.isScriptFile(true)) {
            result.add(child);
          } else {
            addTypes(result, scalaFile, viewSettings);
          }
        }
      } else {
        result.add(treeNode);
      }
    }
    return result;
  }



  private void addTypes(List<AbstractTreeNode> result, ScalaFile file, ViewSettings viewSettings) {
    PsiClass[] classes = file.getClasses();
    for (PsiClass aClass : classes) {
      if (aClass.isValid()) {
        result.add(new ClassTreeNode(myProject, aClass, viewSettings));
      }
    }
  }

  private void showTypesIfSimpleFileOtherwiseShowFile(ViewSettings settings, List<AbstractTreeNode> result, ScalaFile scalaFile, ViewSettings viewSettings) {
    final List<AbstractTreeNode> fileSubElements = new ArrayList<AbstractTreeNode>();
    addTypes(fileSubElements, scalaFile, viewSettings);
    MyPsiFileNode myPsiFileNode = new MyPsiFileNode(scalaFile, viewSettings, ScalaDefsProjectViewProvider.this.myProject);
    if (myPsiFileNode.onlyOneTypeDefinition()) {
      result.addAll(fileSubElements);
    } else {
      result.add(myPsiFileNode);
    }
  }

  private AbstractTreeNode getUltimateParent(AbstractTreeNode node) {
    AbstractTreeNode ultimateParent = node;
    while (ultimateParent.getParent() != null) {
      ultimateParent = ultimateParent.getParent();
    }
    return ultimateParent;
  }

  @Nullable
  public Object getData(Collection<AbstractTreeNode> selected, String dataName) {
    return null;
  }

  private class MyPsiFileNode extends PsiFileNode {
    private final ScalaFile scalaFile;
    private final ViewSettings viewSettings;

    public MyPsiFileNode(ScalaFile scalaFile, ViewSettings viewSettings, Project myProject) {
      super(myProject, scalaFile, viewSettings);
      this.scalaFile = scalaFile;
      this.viewSettings = viewSettings;
    }

    @Override
    public Collection<AbstractTreeNode> getChildrenImpl() {
      final List<AbstractTreeNode> fileSubElements = new ArrayList<AbstractTreeNode>();
      addTypes(fileSubElements, scalaFile, viewSettings);
      return fileSubElements;
    }

    @Override
    protected void updateImpl(PresentationData presentationData) {
      super.updateImpl(presentationData);
      presentationData.setIcons(Icons.FILE_TYPE_LOGO);
    }

    public boolean onlyOneTypeDefinition() {
      return scalaFile.typeDefinitions().length == 1;
    }

    @Override
     public boolean isAlwaysExpand() {
      return false;
    }

    @Deprecated
    public boolean onlyTypesWithSameNameAsFile() {
      Collection<AbstractTreeNode> impl = getChildrenImpl();
      for (AbstractTreeNode abstractTreeNode : impl) {
        Object anObject = abstractTreeNode.getValue();
        if (anObject instanceof ScTypeDefinition) {
          if (!typeBelongsInFile((ScTypeDefinition) anObject)) {
            return false;
          }
        } else {
          return false;
        }
      }
      return true;
    }

    @Deprecated
    private boolean typeBelongsInFile(ScTypeDefinition scTypeDefinition) {
      return scalaFile.getName().equals(scTypeDefinition.getName() + ".scala");
    }
  }
}