package org.jetbrains.plugins.scala.lang.refactoring.move;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.move.moveClassesOrPackages.MoveClassHandler;
import com.intellij.usageView.UsageInfo;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.scala.ScalaFileType;
import org.jetbrains.plugins.scala.conversion.copy.Associations;
import org.jetbrains.plugins.scala.conversion.copy.ScalaCopyPastePostProcessor;
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile;
import org.jetbrains.plugins.scala.lang.psi.api.ScalaRecursiveElementVisitor;
import org.jetbrains.plugins.scala.lang.psi.api.base.ScReferenceElement;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

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
    final PsiPackage newPackage = JavaDirectoryService.getInstance().getPackage(moveDestination);

    PsiClass newClass = null;
    if (file instanceof ScalaFile) {
      if (!moveDestination.equals(file.getContainingDirectory()) &&
           moveDestination.findFile(file.getName()) != null) {
        // moving second of two classes which were in the same file to a different directory (IDEADEV-3089)
//        correctSelfReferences(aClass, newPackage);
        final PsiFile newFile = moveDestination.findFile(file.getName());
        LOG.assertTrue(newFile != null);
        newClass = (PsiClass)newFile.add(aClass);
        correctOldClassReferences(newClass, aClass);
        aClass.delete();
      }
      else if (((ScalaFile)file).typeDefinitionsArray().length > 1) {
//        correctSelfReferences(aClass, newPackage);
        final PsiClass created = ScalaDirectoryService.createClassFromTemplate(moveDestination, aClass.getName(), "Scala Class", false);
//        if (aClass.getDocComment() == null) {
          final PsiDocComment createdDocComment = created.getDocComment();
          if (createdDocComment != null) {
//            aClass.addAfter(createdDocComment, null);
            createdDocComment.delete();
          }
//        }
        newClass = (PsiClass)created.replace(aClass);
        correctOldClassReferences(newClass, aClass);
        aClass.delete();
      }
    }
    return newClass;
  }

  private static void correctOldClassReferences(final PsiClass newClass, final PsiClass oldClass) {
    final Set<PsiImportStatementBase> importsToDelete = new HashSet<PsiImportStatementBase>();
    newClass.getContainingFile().accept(new ScalaRecursiveElementVisitor() {
      @Override
      public void visitReference(ScReferenceElement reference) {
        if (reference.isReferenceTo(oldClass)) {
          final PsiImportStatementBase importStatement = PsiTreeUtil.getParentOfType(reference, PsiImportStatementBase.class);
          if (importStatement != null) {
            importsToDelete.add(importStatement);
            return;
          }
          try {
            reference.bindToElement(newClass);
          }
          catch (IncorrectOperationException e) {
            LOG.error(e);
          }
        }
        super.visitReference(reference);
      }
    });
    for (PsiImportStatementBase importStatement : importsToDelete) {
      importStatement.delete();
    }
  }

  private static void correctSelfReferences(final PsiClass aClass, final PsiPackage newContainingPackage) {
    final PsiPackage aPackage = JavaDirectoryService.getInstance().getPackage(aClass.getContainingFile().getContainingDirectory());
    if (aPackage != null) {
      aClass.accept(new JavaRecursiveElementWalkingVisitor() {
        @Override
        public void visitReferenceElement(PsiJavaCodeReferenceElement reference) {
          if (reference.isQualified() && reference.isReferenceTo(aClass)) {
            final PsiElement qualifier = reference.getQualifier();
            if (qualifier instanceof PsiJavaCodeReferenceElement && ((PsiJavaCodeReferenceElement)qualifier).isReferenceTo(aPackage)) {
              try {
                ((PsiJavaCodeReferenceElement)qualifier).bindToElement(newContainingPackage);
              }
              catch (IncorrectOperationException e) {
                LOG.error(e);
              }
            }
          }
          super.visitReferenceElement(reference);
        }
      });
    }
  }

  public String getName(PsiClass clazz) {
    final PsiFile file = clazz.getContainingFile();
    if (!(file instanceof ScalaFile)) return null;
    return ((ScalaFile)file).typeDefinitionsArray().length > 1 ? clazz.getName() + "." + ScalaFileType.SCALA_FILE_TYPE.getDefaultExtension() : file.getName();
  }

  @Override
  public void preprocessUsages(Collection<UsageInfo> results) {
  }
}
