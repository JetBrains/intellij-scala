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

package org.jetbrains.plugins.scala.lang.lexer.core;

import com.intellij.lexer.FlexAdapter;
import com.intellij.lexer.MergingLexerAdapter;
import com.intellij.psi.tree.TokenSet;
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypesEx;
import org.jetbrains.plugins.scala.lang.scaladoc.parser.ScalaDocElementTypes;

import java.io.Reader;

/**
 * @author ilyas
 */

public class ScalaSplittingLexer extends MergingLexerAdapter implements ScalaTokenTypesEx, ScalaDocElementTypes {

  public ScalaSplittingLexer() {
    super(new ScalaSplittingFlexLexer(),
        TokenSet.create(
            SCALA_DOC_COMMENT,
            tBLOCK_COMMENT,
            SCALA_PLAIN_CONTENT
        ));
  }

  private static class ScalaSplittingFlexLexer extends FlexAdapter {
    public ScalaSplittingFlexLexer() {
      super(new _ScalaSplittingLexer((Reader) null));
    }
  }
}

