/*
 * Copyright 2000-2008 JetBrains s.r.o.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jetbrains.plugins.scala.lang.lexer;

import com.intellij.psi.tree.IElementType;

public interface ScalaTokenTypesEx extends ScalaTokenTypes {

  IElementType SCALA_PLAIN_CONTENT = new ScalaTokenType("ScalaContent");
  IElementType SCALA_XML_CONTENT_START = new ScalaTokenType("XmlContentStart");
  IElementType SCALA_XML_CONTENT = new ScalaTokenType("ScalaXmlContent");

  IElementType SCALA_IN_XML_INJECTION_START = new ScalaTokenType("ScalaXmlInjectionStart");
  IElementType SCALA_IN_XML_INJECTION_END = new ScalaTokenType("ScalaXmlInjection End");

//  IElementType WHITESPACE_INTERMEDIATE = new ScalaElementType("Strange whitespace");

}
