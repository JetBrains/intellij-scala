package org.jetbrains.plugins.scala.lang.lexer.core;

import com.intellij.lexer.FlexLexer;
import com.intellij.psi.tree.IElementType;
import java.util.*;
import java.lang.reflect.Field;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypesEx;

%%

%class _ScalaSplittingLexer
%implements FlexLexer, ScalaTokenTypesEx
%unicode
%public

%function advance
%type IElementType

%eof{ return;
%eof}

////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
/////////////////////// USER CODE //////////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

%{

  private int braceCount = 0;
  private IElementType commentType = tBLOCK_COMMENT;

%}

COMMENT_BEGIN = "/*"
SIMPLE_BLOCK_COMMENT = "/**/"
DOC_COMMENT_BEGIN = "/*""*"
COMMENT_END = "*/"


////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
////////////////////  states ///////////////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

%xstate IN_BLOCK_COMMENT

%%

<YYINITIAL>{

{SIMPLE_BLOCK_COMMENT}   {  return(tBLOCK_COMMENT); }

{DOC_COMMENT_BEGIN}      {  commentType = tDOC_COMMENT;
                            braceCount++;
                            yybegin(IN_BLOCK_COMMENT);
                            return(commentType);
                         }

{COMMENT_BEGIN}          {  commentType = tBLOCK_COMMENT;
                            braceCount++;
                            yybegin(IN_BLOCK_COMMENT);
                            return(commentType);
                         }

[^]                      {  return SCALA_CONTENT; }

}

////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
/////////////////////////  Block comment processing ////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
<IN_BLOCK_COMMENT> {

{COMMENT_BEGIN}          {  braceCount++;
                            return(commentType);
                         }

{COMMENT_END}            {  braceCount--;
                            if (braceCount == 0) {
                              yybegin(YYINITIAL);
                            }
                            return(commentType);
                         }

[^]                      {  return(commentType); }

}



