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

import com.intellij.ide.projectView.ProjectViewNode;
import com.intellij.ide.projectView.TreeStructureProvider;
import com.intellij.ide.projectView.ViewSettings;
import com.intellij.ide.projectView.impl.nodes.ClassTreeNode;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile;

import java.util.ArrayList;
import java.util.Collection;

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
    ArrayList<AbstractTreeNode> result = new ArrayList<AbstractTreeNode>();

    for (final AbstractTreeNode child : children) {
      ProjectViewNode treeNode = (ProjectViewNode) child;
      Object o = treeNode.getValue();
      if (o instanceof ScalaFile) {
        ScalaFile scalaFile = (ScalaFile) o;

        ViewSettings viewSettings = ((ProjectViewNode) parent).getSettings();
        if (scalaFile.typeDefinitions().length == 0 || scalaFile.isScriptFile()) {
          result.add(child);
        }

        addTypes(result, scalaFile, viewSettings);
      } else
        result.add(treeNode);
    }
    return result;
  }

  private void addTypes(ArrayList<AbstractTreeNode> result, ScalaFile file, ViewSettings viewSettings) {
    PsiClass[] classes = file.getClasses();
    if (classes.length != 0) {
      for (PsiClass aClass : classes) {
        if (aClass.isValid()) {
          result.add(new ClassTreeNode(myProject, aClass, viewSettings));
        }
      }
    }
  }

  @Nullable
  public Object getData(Collection<AbstractTreeNode> selected, String dataName) {
    return null;
  }
}