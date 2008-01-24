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

package org.jetbrains.plugins.scala.compiler;

import com.intellij.execution.process.OSProcessHandler;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.ExecutionException;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.compiler.CompileContext;
import com.intellij.openapi.compiler.CompilerMessageCategory;
import com.intellij.openapi.compiler.TranslatingCompiler;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.Application;
import com.intellij.util.containers.HashSet;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiClass;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.search.GlobalSearchScope;

import javax.swing.*;
import java.io.File;
import java.util.Set;
import java.util.Collections;
import java.awt.*;
import java.lang.reflect.InvocationTargetException;

/**
 * @author ven
 */
public class ScalacOSProcessHandler extends OSProcessHandler {
  private static final Logger LOG = Logger.getInstance("org.jetbrains.plugins.scala.compiler.ScalacOSProcessHandler");
  private CompileContext myContext;
  private Project myProject;
  private Integer myLineNumber;
  private String myMessage;
  private String myUrl;

  private Set<TranslatingCompiler.OutputItem> mySuccessfullyCompiledSources;

  public Set<TranslatingCompiler.OutputItem> getSuccessfullyCompiled() {
    return Collections.unmodifiableSet(mySuccessfullyCompiledSources);
  }

  public ScalacOSProcessHandler(GeneralCommandLine commandLine, CompileContext context, Project project) throws ExecutionException {
    super(commandLine.createProcess(), commandLine.getCommandLineString());
    myContext = context;
    myProject = project;
    mySuccessfullyCompiledSources = new HashSet<TranslatingCompiler.OutputItem>();
  }


  public void notifyTextAvailable(final String text, final Key outputType) {
    super.notifyTextAvailable(text, outputType);
    parseOutput(text);
  }

  private static final String ourErrorMarker = " error:";
  private static final String ourInfoMarkerStart = "[";
  private static final String ourInfoMarkerEnd = "]";
  private static final String ourParsingMarker = "parsing";
  private static final String ourWroteMarker = "wrote ";
  private static final String ourColumnMarker = "^";

  private boolean myColumnOnNextLine = false;


  private void parseOutput(String text) {
    if (myMessage != null) {
      if (myColumnOnNextLine) {
        int column = text.indexOf(ourColumnMarker) + 1;
        if (column < 0) column = 1;
        myContext.addMessage(CompilerMessageCategory.ERROR, myMessage, myUrl, myLineNumber, column);
        myMessage = null;
        myColumnOnNextLine = false;
      } else {
        myColumnOnNextLine = true;
      }
      return;
    }

    text = text.trim();
    if (text.endsWith("\r\n")) text = text.substring(0, text.length() - 2);
    if (text.length() == 1 && text.charAt(0) == '^') return;

    int i = text.indexOf(ourErrorMarker);
    if (i > 0) {
      String errorPlace = text.substring(0, i);
      if (errorPlace.endsWith(":"))
        errorPlace = errorPlace.substring(0, errorPlace.length() - 1); //hack over compiler output
      int j = errorPlace.lastIndexOf(':');
      if (j > 0) {
        myUrl = VirtualFileManager.constructUrl(LocalFileSystem.PROTOCOL,
            errorPlace.substring(0, j).replace(File.separatorChar, '/'));

        try {
          myLineNumber = Integer.valueOf(errorPlace.substring(j + 1, errorPlace.length()));
          myMessage = text.substring(i + 1).trim();
        } catch (NumberFormatException e) {
          myContext.addMessage(CompilerMessageCategory.INFORMATION, "", text, -1, -1);
        }
      } else {
        myContext.addMessage(CompilerMessageCategory.INFORMATION, "", text, -1, -1);
      }
    } else {
      if (text.startsWith(ourInfoMarkerStart) && text.endsWith(ourInfoMarkerEnd)) {  //verbose compiler output
        String info = text.substring(ourInfoMarkerStart.length(), text.length() - ourInfoMarkerEnd.length());
        if (info.startsWith(ourParsingMarker)) {
          myContext.getProgressIndicator().setText(info);
        } else if (info.startsWith(ourWroteMarker)) {
          myContext.getProgressIndicator().setText(info);
          String s = info.substring(ourWroteMarker.length());
          int w = s.indexOf(' ');
          String outputPath = w > 0 ? s.substring(0, w) : s;
          try {
            TranslatingCompiler.OutputItem item = getOutputItem(outputPath.replace(File.separatorChar, '/'));
            if (item != null) {
              mySuccessfullyCompiledSources.add(item);
            }
          } catch (InvocationTargetException e) {
          } catch (InterruptedException e) {
                      } }
      }
    }
  }

  private TranslatingCompiler.OutputItem getOutputItem(final String outputPath) throws InvocationTargetException, InterruptedException {
    final Application application = ApplicationManager.getApplication();
    final TranslatingCompiler.OutputItem[] result = new TranslatingCompiler.OutputItem[1];
    SwingUtilities.invokeAndWait(new Runnable() {
      public void run() {
        result[0] = application.runWriteAction(new Computable<TranslatingCompiler.OutputItem>() {
          public TranslatingCompiler.OutputItem compute() {
            VirtualFile classFile = LocalFileSystem.getInstance().refreshAndFindFileByPath(outputPath.replace(File.separatorChar, '/'));
            LOG.assertTrue(classFile != null);
            final VirtualFile outputRoot = getOutputRoot(classFile);
            final String fileName = VfsUtil.getRelativePath(classFile, outputRoot, '.');
            assert fileName.endsWith(".class");
            final String fqName = fileName.substring(0, fileName.length() - ".class".length());
            final PsiManager manager = PsiManager.getInstance(myProject);
            final JavaPsiFacade facade = JavaPsiFacade.getInstance(myProject);
            PsiClass aClass = application.runReadAction(new Computable<PsiClass>() {
              public PsiClass compute() {
                return facade.findClass(fqName, GlobalSearchScope.projectScope(myProject));
              }
            });
            if (aClass == null) return null;
            final VirtualFile sourceFile = aClass.getContainingFile().getVirtualFile();
            return new TranslatingCompiler.OutputItem() {
              public String getOutputPath() {
                return outputPath;
              }

              public VirtualFile getSourceFile() {
                return sourceFile;
              }

              public String getOutputRootDirectory() {
                return outputRoot.getPath();
              }
            };
          }
        });
      }
    });
    return result[0];
  }

  private VirtualFile getOutputRoot(VirtualFile classFile) {
    final VirtualFile[] outputs = myContext.getAllOutputDirectories();
    for (final VirtualFile output : outputs) {
      if (VfsUtil.isAncestor(output, classFile, true)) {
        return output;
      }
    }

    LOG.assertTrue(false);
    return null;
  }
}
