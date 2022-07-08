/*
 * Copyright 2000-2011 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jetbrains.plugins.scala.lang.refactoring.move;

import com.intellij.codeInsight.ChangeContextUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.refactoring.move.moveClassesOrPackages.MoveClassesOrPackagesUtil;
import com.intellij.refactoring.move.moveFilesOrDirectories.MoveFileHandler;
import com.intellij.refactoring.util.MoveRenameUsageInfo;
import com.intellij.usageView.UsageInfo;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.plugins.scala.editor.importOptimizer.ScalaImportOptimizer;
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile;
import org.jetbrains.plugins.scala.lang.refactoring.Associations;
import org.jetbrains.plugins.scala.statistics.FeatureKey;
import org.jetbrains.plugins.scala.statistics.Stats;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.jetbrains.plugins.scala.lang.refactoring.util.ScalaChangeContextUtil.encodeContextInfo;

public class MoveScalaFileHandler extends MoveFileHandler {

  private static final Logger LOG = Logger.getInstance("#org.jetbrains.plugins.scala.lang.refactoring.move.MoveScalaFileHandler");

  @Override
  public boolean canProcessElement(PsiFile element) {
    if (!(element instanceof ScalaFile)) return false;

    final VirtualFile file = element.getVirtualFile();
    if (file == null) return false;
    final ProjectFileIndex projectFileIndex = ProjectRootManager.getInstance(element.getProject()).getFileIndex();
    return !projectFileIndex.isInSource(file) && !projectFileIndex.isInLibraryClasses(file);
  }

  @Override
  public void prepareMovedFile(PsiFile file, PsiDirectory moveDestination, Map<PsiElement, PsiElement> oldToNewMap) {
    if (file instanceof ScalaFile) {
      Stats.trigger(FeatureKey.moveFile());
      ChangeContextUtil.encodeContextInfo(file, true);
      encodeContextInfo(file);
    }
  }

  @Override
  public List<UsageInfo> findUsages(PsiFile psiFile, PsiDirectory newParent, boolean searchInComments, boolean searchInNonJavaFiles) {
    final List<UsageInfo> result = new ArrayList<UsageInfo>();
    if (psiFile instanceof ScalaFile) {
      final PsiPackage newParentPackage = JavaDirectoryService.getInstance().getPackage(newParent);
      final String qualifiedName = newParentPackage == null ? "" : newParentPackage.getQualifiedName();
      for (PsiClass aClass : ((ScalaFile)psiFile).getClasses()) {
        Collections.addAll(
                result,
                MoveClassesOrPackagesUtil.findUsages(
                        aClass,
                        GlobalSearchScope.projectScope(aClass.getProject()),
                        searchInComments,
                        searchInNonJavaFiles,
                        StringUtil.getQualifiedName(qualifiedName, aClass.getName())
                )
        );
      }
    }
    return result.isEmpty() ? null : result;
  }

  @Override
  public void retargetUsages(List<UsageInfo> usageInfos, Map<PsiElement, PsiElement> oldToNewMap) {
    for (UsageInfo usage : usageInfos) {
      if (usage instanceof MoveRenameUsageInfo) {
        final MoveRenameUsageInfo moveRenameUsage = (MoveRenameUsageInfo)usage;
        final PsiElement oldElement = moveRenameUsage.getReferencedElement();
        final PsiElement newElement = oldToNewMap.get(oldElement);
        final PsiReference reference = moveRenameUsage.getReference();
        if (reference != null) {
          try {
            LOG.assertTrue(newElement != null, oldElement != null ? oldElement : reference);
            reference.bindToElement(newElement);
          } catch (IncorrectOperationException ex) {
            LOG.error(ex);
          }
        }
      }
    }
  }

  @Override
  public void updateMovedFile(PsiFile file) throws IncorrectOperationException {
    if (file instanceof ScalaFile) {
      ChangeContextUtil.decodeContextInfo(file, null, null);
      Associations.restoreFor(file);
      new ScalaImportOptimizer().processFile(file).run();
    }
  }
}