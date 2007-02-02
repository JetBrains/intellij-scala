package org.jetbrains.plugins.scala.compiler;

import com.intellij.execution.process.OSProcessHandler;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.ExecutionException;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.compiler.CompileContext;
import com.intellij.openapi.compiler.CompilerMessageCategory;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.LocalFileSystem;

import java.io.File;

/**
 * @author ven
 */
public class ScalacOSProcessHandler extends OSProcessHandler {
  private CompileContext myContext;
  private Integer myLineNumber;
  private String myMessage;
  private String myUrl;

  public ScalacOSProcessHandler(GeneralCommandLine commandLine, CompileContext context) throws ExecutionException {
    super(commandLine.createProcess(), commandLine.getCommandLineString());
    myContext = context;
  }


  public void notifyTextAvailable(final String text, final Key outputType) {
    super.notifyTextAvailable(text, outputType);
    parseOutput(text);
  }

  private static final String ourErrorMarker = ": error:";
  private static final String ourInfoMarkerStart = "[";
  private static final String ourInfoMarkerEnd = "]";
  private static final String ourParsingMarker = "parsing";
  private static final String ourWroteMarker = "wrote";
  private static final String ourColumnMarker = "^";

  private boolean myColumnOnNextLine = false;


  private void parseOutput(String text) {
    if (myMessage != null) {
      if (myColumnOnNextLine) {
        int column = text.indexOf(ourColumnMarker);
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
    if (text.length() == 1 && text.charAt(0) == '^')  return;

    int i = text.indexOf(ourErrorMarker);
    if (i > 0) {
      String errorPlace = text.substring(0, i);
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
      }
    } else {
        if (text.startsWith(ourInfoMarkerStart) && text.endsWith(ourInfoMarkerEnd)) {  //verbose compiler output
          String info = text.substring(ourInfoMarkerStart.length(), text.length() - ourInfoMarkerEnd.length());
            if (info.startsWith(ourParsingMarker) || info.startsWith(ourWroteMarker)) {
                myContext.getProgressIndicator().setText(info);
            }
        } else {
          myContext.addMessage(CompilerMessageCategory.INFORMATION, text, null, -1, -1);
        }
    }
  }
}
