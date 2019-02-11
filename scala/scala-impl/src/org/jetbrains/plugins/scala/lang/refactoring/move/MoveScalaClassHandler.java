package org.jetbrains.plugins.scala.lang.refactoring.move;

import com.intellij.openapi.editor.Document;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.refactoring.move.moveClassesOrPackages.MoveClassHandler;
import com.intellij.usageView.UsageInfo;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.scala.ScalaFileType;
import org.jetbrains.plugins.scala.editor.importOptimizer.ScalaImportOptimizer;
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile;
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScNamedElement;
import org.jetbrains.plugins.scala.statistics.FeatureKey;
import org.jetbrains.plugins.scala.statistics.Stats;

import java.util.Collection;

public class MoveScalaClassHandler implements MoveClassHandler {

  @Override
  public void finishMoveClass(@NotNull PsiClass aClass) {
    PsiFile file = aClass.getContainingFile();
    if (file instanceof ScalaFile) {
      package$.MODULE$.restoreAssociations(aClass);
      PsiDocumentManager documentManager = PsiDocumentManager.getInstance(file.getProject());
      Document document = documentManager.getDocument(file);
      if (document == null) return;
      documentManager.doPostponedOperationsAndUnblockDocument(document);
      new ScalaImportOptimizer().processFile(file).run();
    }
  }

  @Override
  public void prepareMove(@NotNull PsiClass aClass) {
    if (aClass.getContainingFile() instanceof ScalaFile) {
      Stats.trigger(FeatureKey.moveClass());
      package$.MODULE$.collectAssociations(aClass);
    }
  }

  public PsiClass doMoveClass(@NotNull final PsiClass aClass, @NotNull PsiDirectory moveDestination) throws IncorrectOperationException {
    return package$.MODULE$.doMoveClass(aClass, moveDestination);
  }

  public String getName(PsiClass clazz) {
    final PsiFile file = clazz.getContainingFile();
    if (!(file instanceof ScalaFile)) return null;

    ScalaFile scalaFile = (ScalaFile) file;
    boolean hasMultiple = scalaFile.typeDefinitions().length() > 1;
    return hasMultiple ?
            ((ScNamedElement) clazz).name() + "." + ScalaFileType.INSTANCE.getDefaultExtension() :
            file.getName();
  }

  @Override
  public void preprocessUsages(Collection<UsageInfo> results) {
  }
}
