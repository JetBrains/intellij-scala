package org.jetbrains.plugins.scala.findUsages.compilerReferences;

import com.intellij.compiler.backwardRefs.CompilerReferenceReader;
import com.intellij.compiler.backwardRefs.CompilerReferenceReaderFactory;
import com.intellij.compiler.backwardRefs.CompilerReferenceServiceBase;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.util.messages.MessageBusConnection;

import java.util.Set;
import java.util.function.BiConsumer;

// used to workaround a bug in scalac
class CompilerReferenceServiceAdapter<Reader extends CompilerReferenceReader<?>> extends CompilerReferenceServiceBase<Reader> {
  protected CompilerReferenceServiceAdapter(Project project, FileDocumentManager fileDocumentManager, PsiDocumentManager psiDocumentManager,
                                         CompilerReferenceReaderFactory<Reader> readerFactory,
                                         BiConsumer<MessageBusConnection, Set<String>> compilationAffectedModulesSubscription) {
    super(project, fileDocumentManager, psiDocumentManager, readerFactory, compilationAffectedModulesSubscription);
  }

  protected void resetReader() { myReader = null; }
}
