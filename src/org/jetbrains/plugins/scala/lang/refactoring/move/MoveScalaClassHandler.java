package org.jetbrains.plugins.scala.lang.refactoring.move;

import com.intellij.internal.statistic.UsageTrigger;
import com.intellij.openapi.editor.Document;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.refactoring.move.moveClassesOrPackages.MoveClassHandler;
import com.intellij.usageView.UsageInfo;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.scala.ScalaBundle;
import org.jetbrains.plugins.scala.ScalaFileType;
import org.jetbrains.plugins.scala.editor.importOptimizer.ScalaImportOptimizer;
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile;
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScNamedElement;
import org.jetbrains.plugins.scala.settings.ScalaApplicationSettings;

import java.util.Collection;

public class MoveScalaClassHandler implements MoveClassHandler {

  @Override
  public void finishMoveClass(@NotNull PsiClass aClass) {
    PsiFile file = aClass.getContainingFile();
    if (file instanceof ScalaFile) {
      ScalaMoveUtil.restoreAssociations(aClass, ScalaApplicationSettings.getInstance().MOVE_COMPANION);
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
      UsageTrigger.trigger(ScalaBundle.message("move.class.id"));
      ScalaMoveUtil.collectAssociations(aClass, ScalaApplicationSettings.getInstance().MOVE_COMPANION);
    }
  }

  public PsiClass doMoveClass(@NotNull final PsiClass aClass, @NotNull PsiDirectory moveDestination) throws IncorrectOperationException {
    return ScalaMoveUtil.doMoveClass(aClass, moveDestination, ScalaApplicationSettings.getInstance().MOVE_COMPANION);
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
