package org.jetbrains.plugins.scala.lang.refactoring.move;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiFile;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.refactoring.move.moveClassesOrPackages.MoveClassHandler;
import com.intellij.usageView.UsageInfo;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.scala.ScalaFileType;
import org.jetbrains.plugins.scala.conversion.copy.Associations;
import org.jetbrains.plugins.scala.conversion.copy.ScalaCopyPastePostProcessor;
import org.jetbrains.plugins.scala.editor.importOptimizer.ScalaImportOptimizer;
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile;
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScNamedElement;

import java.util.Collection;

public class MoveScalaClassHandler implements MoveClassHandler {
  private static final ScalaCopyPastePostProcessor PROCESSOR = new ScalaCopyPastePostProcessor();
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.plugins.scala.lang.refactoring.move.MoveJavaClassHandler");

  public static final Key<Associations> ASSOCIATIONS_KEY = Key.create("ASSOCIATIONS");

  @Override
  public void finishMoveClass(@NotNull PsiClass aClass) {
    if (aClass.getContainingFile() instanceof ScalaFile) {
      Associations associations = aClass.getCopyableUserData(ASSOCIATIONS_KEY);
      if (associations != null) {
        PROCESSOR.restoreAssociations(associations, aClass.getContainingFile(),
            aClass.getTextRange().getStartOffset(), aClass.getProject());
        aClass.putCopyableUserData(ASSOCIATIONS_KEY, null);
      }
      new ScalaImportOptimizer().processFile(aClass.getContainingFile()).run();
    }
  }

  @Override
  public void prepareMove(@NotNull PsiClass aClass) {
    PsiFile file = aClass.getContainingFile();
    if (file instanceof ScalaFile) {
      TextRange range = aClass.getTextRange();
      Associations associations = PROCESSOR.collectTransferableData(file, null,
          new int[]{range.getStartOffset()}, new int[]{range.getEndOffset()});
      aClass.putCopyableUserData(ASSOCIATIONS_KEY, associations);
    }
  }

  public PsiClass doMoveClass(@NotNull final PsiClass aClass, @NotNull PsiDirectory moveDestination) throws IncorrectOperationException {
    PsiFile file = aClass.getContainingFile();

    PsiClass newClass = null;
    if (file instanceof ScalaFile) {
      if (!moveDestination.equals(file.getContainingDirectory()) &&
           moveDestination.findFile(file.getName()) != null) {
        // moving second of two classes which were in the same file to a different directory (IDEADEV-3089)
        final PsiFile newFile = moveDestination.findFile(file.getName());
        LOG.assertTrue(newFile != null);
        newClass = (PsiClass)newFile.add(aClass);

        aClass.delete();
      }
      else if (((ScalaFile)file).typeDefinitionsArray().length > 1) {
        final PsiClass created = ScalaDirectoryService.createClassFromTemplate(moveDestination, ((ScNamedElement) aClass).name(), "Scala Class", false);
//        if (aClass.getDocComment() == null) {
          final PsiDocComment createdDocComment = created.getDocComment();
          if (createdDocComment != null) {
//            aClass.addAfter(createdDocComment, null);
            createdDocComment.delete();
          }
//        }
        newClass = (PsiClass)created.replace(aClass);
        aClass.delete();
      }
    }
    return newClass;
  }

  public String getName(PsiClass clazz) {
    final PsiFile file = clazz.getContainingFile();
    if (!(file instanceof ScalaFile)) return null;
    return ((ScalaFile)file).typeDefinitionsArray().length > 1
        ? ((ScNamedElement) clazz).name() + "." + ScalaFileType.SCALA_FILE_TYPE.getDefaultExtension()
        : file.getName();
  }

  @Override
  public void preprocessUsages(Collection<UsageInfo> results) {
  }
}
