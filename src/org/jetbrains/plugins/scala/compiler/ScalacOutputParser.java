package org.jetbrains.plugins.scala.compiler;

import com.intellij.compiler.OutputParser;
import com.intellij.compiler.impl.javaCompiler.FileObject;
import com.intellij.openapi.compiler.CompilerMessageCategory;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFileManager;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import static org.jetbrains.plugins.scala.compiler.ScalacOutputParser.MESSAGE_TYPE.*;

import java.io.File;
import java.util.ArrayList;
import java.util.Set;
import java.util.HashSet;
import java.util.Arrays;

/**
 * @author ilyas
 */
class ScalacOutputParser extends OutputParser {

  @NonNls
  private static final String ourErrorMarker = " error:";
  @NonNls
  private static final String ourWarningMarker = " warning:";
  @NonNls
  private static final String ourInfoMarkerStart = "[";
  @NonNls
  private static final String ourInfoMarkerEnd = "]";
  @NonNls
  private static final String ourWroteMarker = "wrote ";
  @NonNls
  private static final String ourColumnMarker = "^";
  @NonNls
  private static final String ourParsingMarker = "parsing ";
  @NonNls
  private static final String ourScalaInternalErrorMsg = "Scalac internal error";

  // Phases
  @NonNls
  private static final String PHASE = "running phase ";
  @NonNls
  private static final String PARSER_ON = "parser on ";

  @NonNls
  private static Set<String> PHASES = new HashSet<String>();
  static {
    PHASES.addAll(Arrays.asList(
        "parser", "namer", "typer", "superaccessors", "pickler", "refchecks", "liftcode",
        "uncurry", "tailcalls", "explicitouter", "cleanup"
    ));
  }

  private boolean mustProcessMsg = false;
  private boolean fullCrash = false;
  private boolean stopProcessing = false;
  private int myMsgColumnMarker;
  private MESSAGE_TYPE myMsgType = PLAIN;
  private ArrayList<String> myWrittenList = new ArrayList<String>();
  private final Object WRITTEN_LIST_LOCK = new Object();

  static enum MESSAGE_TYPE {
    ERROR, WARNING, PLAIN
  }

  private Integer myLineNumber;
  private String myMessage;
  private String myUrl;

  @Override
  public boolean processMessageLine(Callback callback) {
    final String line = callback.getNextLine();

    if (line == null) {
      flushWrittenList(callback);
      return false;
    }

    String text = line.trim();
    if (fullCrash && text.length( ) > 0) {
      callback.message(CompilerMessageCategory.ERROR, text, "", 0, 0);
      return true;
    }

    if (text.endsWith("\r\n")) text = text.substring(0, text.length() - 2);
    if (text.startsWith(ourScalaInternalErrorMsg)) {
      callback.message(CompilerMessageCategory.ERROR, text, "", 0, 0);
      fullCrash = true;
      return true;
    }

    // Add error message to output
    if (myMessage != null && stopMsgProcessing(text) && (mustProcessMsg || stopProcessing)) {
      myMsgColumnMarker = myMsgColumnMarker > 0 ? myMsgColumnMarker : 1;
      if (myMsgType == ERROR) {
        callback.message(CompilerMessageCategory.ERROR, myMessage, myUrl, myLineNumber, myMsgColumnMarker);
      } else if (myMsgType == WARNING) {
        callback.message(CompilerMessageCategory.WARNING, myMessage, myUrl, myLineNumber, myMsgColumnMarker);
      }
      myMessage = null;
      myMsgType = PLAIN;
      mustProcessMsg = false;
      stopProcessing = false;
    }

    if (text.indexOf(ourErrorMarker) > 0) { // new error occurred
      processErrorMesssage(text, text.indexOf(ourErrorMarker), ERROR, callback);
    } else if (text.indexOf(ourWarningMarker) > 0) {
      processErrorMesssage(text, text.indexOf(ourWarningMarker), WARNING, callback);
    } else if (!text.startsWith(ourInfoMarkerStart)) { //  continuing process [error | warning] message
      if (mustProcessMsg) {
        if (ourColumnMarker.equals(text.trim())) {
          myMsgColumnMarker = line.indexOf(ourColumnMarker) + 1;
          stopProcessing = true;
        } else if (myMessage != null) {
          if (myMsgType != WARNING) {
            myMessage += "\n" + text;
          }
        } else {
          mustProcessMsg = false;
        }
      }
    } else { //verbose compiler output
      mustProcessMsg = false;
      if (text.endsWith(ourInfoMarkerEnd)) {
        String info = text.substring(ourInfoMarkerStart.length(), text.length() - ourInfoMarkerEnd.length());
        if (info.startsWith(ourParsingMarker)) { //parsing
          callback.setProgressText(info);
          // Set file processed
          callback.fileProcessed(info.substring(info.indexOf(ourParsingMarker) + ourParsingMarker.length()));
        }
        //add phases and their times to output
        else if (getPhaseName(info) != null) {
          callback.setProgressText("Phase " + getPhaseName(info) + " passed" + info.substring(getPhaseName(info).length()));
        } else if (info.startsWith("loaded")) {
          callback.setProgressText("Loading files...");
        } else if (info.startsWith(ourWroteMarker)) {
          callback.setProgressText(info);
          String outputPath = info.substring(ourWroteMarker.length());
          final String path = outputPath.replace(File.separatorChar, '/');
//          callback.fileGenerated(path);
          synchronized (WRITTEN_LIST_LOCK) {
            myWrittenList.add(path);
          }
        }
      }
    }
    return true;
  }

  private static String getPhaseName(@NotNull String st) {
    for (String phase : PHASES) {
      if (st.startsWith(phase + " ")) return phase;
    }
    return null;
  }

  public void flushWrittenList(Callback callback) {
    synchronized (WRITTEN_LIST_LOCK) {
    //ensure that all "written" files are really written
      for (String s : myWrittenList) {
        File out = new File(s);
        callback.fileGenerated(new FileObject(out));
      }
      myWrittenList.clear();
    }
  }

  private boolean stopMsgProcessing(String text) {
    return text.startsWith(ourInfoMarkerStart) && !text.trim().equals(ourColumnMarker) ||
            text.indexOf(ourErrorMarker) > 0 ||
            text.indexOf(ourWarningMarker) > 0 ||
            stopProcessing;
  }

  /*
 Collect information about error occurrence
  */
  private void processErrorMesssage(String text, int errIndex, MESSAGE_TYPE msgType, Callback callback) {
    String errorPlace = text.substring(0, errIndex);
    if (errorPlace.endsWith(":"))
      errorPlace = errorPlace.substring(0, errorPlace.length() - 1); //hack over compiler output
    int j = errorPlace.lastIndexOf(':');
    if (j > 0) {
      myUrl = VirtualFileManager.constructUrl(LocalFileSystem.PROTOCOL, errorPlace.substring(0, j).replace(File.separatorChar, '/'));
      try {
        myLineNumber = Integer.valueOf(errorPlace.substring(j + 1, errorPlace.length()));
        myMessage = text.substring(errIndex + 1).trim();
        mustProcessMsg = true;
        myMsgType = msgType;
      } catch (NumberFormatException e) {
        callback.message(CompilerMessageCategory.INFORMATION, "", text, -1, -1);
        myMessage = null;
        mustProcessMsg = false;
        myMsgType = PLAIN;
      }
    } else {
      callback.message(CompilerMessageCategory.INFORMATION, "", text, -1, -1);
      myMsgType = PLAIN;
    }
  }

  public boolean isTrimLines() {
    return false;
  }


}
