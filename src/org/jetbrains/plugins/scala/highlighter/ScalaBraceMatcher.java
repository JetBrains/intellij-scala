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

package org.jetbrains.plugins.scala.highlighter;

import com.intellij.lang.PairedBraceMatcher;
import com.intellij.lang.BracePair;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Author: Ilya Sergey
 * Date: 29.09.2006
 * Time: 20:26:52
 */
public class ScalaBraceMatcher implements PairedBraceMatcher {

  private static final BracePair[] PAIRS = new BracePair[]{
          new BracePair('(', ScalaTokenTypes.tLPARENTHESIS, ')', ScalaTokenTypes.tRPARENTHESIS, false),
          new BracePair('[', ScalaTokenTypes.tLSQBRACKET, ']', ScalaTokenTypes.tRSQBRACKET, false),
          new BracePair('{', ScalaTokenTypes.tLBRACE, '}', ScalaTokenTypes.tRBRACE, true)
  };

  public BracePair[] getPairs() {
    
    return PAIRS;
  }

  public boolean isPairedBracesAllowedBeforeType(@NotNull IElementType type, @Nullable IElementType type1) {
    return false;
  }
}
