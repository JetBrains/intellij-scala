/*
 * Copyright 2000-2011 JetBrains s.r.o.
 *
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

/*
 * User: anna
 * Date: 05-Aug-2009
 */
package org.jetbrains.plugins.scala.lang.refactoring.move;

import com.intellij.codeInsight.ChangeContextUtil;
import com.intellij.internal.statistic.UsageTrigger;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.refactoring.move.moveClassesOrPackages.MoveClassesOrPackagesUtil;
import com.intellij.refactoring.move.moveFilesOrDirectories.MoveFileHandler;
import com.intellij.refactoring.util.MoveRenameUsageInfo;
import com.intellij.usageView.UsageInfo;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.plugins.scala.ScalaBundle;
import org.jetbrains.plugins.scala.conversion.copy.Associations;
import org.jetbrains.plugins.scala.conversion.copy.ScalaCopyPastePostProcessor;
import org.jetbrains.plugins.scala.editor.importOptimizer.ScalaImportOptimizer;
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class MoveScalaFileHandler extends MoveFileHandler {
  private static final ScalaCopyPastePostProcessor PROCESSOR = new ScalaCopyPastePostProcessor();
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.plugins.scala.lang.refactoring.move.MoveScalaFileHandler");

  public static final Key<Associations> ASSOCIATIONS_KEY = Key.create("ASSOCIATIONS");

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
      UsageTrigger.trigger(ScalaBundle.message("move.file.id"));
      ChangeContextUtil.encodeContextInfo(file, true);
      TextRange range = file.getTextRange();
      Associations associations = PROCESSOR.collectTransferableData(file, null,
          new int[]{range.getStartOffset()}, new int[]{range.getEndOffset()});
      file.putCopyableUserData(ASSOCIATIONS_KEY, associations);
    }
  }

  public List<UsageInfo> findUsages(PsiFile psiFile, PsiDirectory newParent, boolean searchInComments, boolean searchInNonJavaFiles) {
    final List<UsageInfo> result = new ArrayList<UsageInfo>();
    if (psiFile instanceof ScalaFile) {
      final PsiPackage newParentPackage = JavaDirectoryService.getInstance().getPackage(newParent);
      final String qualifiedName = newParentPackage == null ? "" : newParentPackage.getQualifiedName();
      for (PsiClass aClass : ((ScalaFile)psiFile).getClasses()) {
        Collections.addAll(result, MoveClassesOrPackagesUtil.findUsages(aClass, searchInComments, searchInNonJavaFiles,
            StringUtil.getQualifiedName(qualifiedName, aClass.getName())));
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
      Associations associations = file.getCopyableUserData(ASSOCIATIONS_KEY);
      if (associations != null) {
        try {
          PROCESSOR.restoreAssociations(associations, file,
              file.getTextRange().getStartOffset(), file.getProject());
        } finally {
          file.putCopyableUserData(ASSOCIATIONS_KEY, null);
        }
      }
      new ScalaImportOptimizer().processFile(file).run();
    }
  }
}