/*
 * Copyright 2001-2008 Artima, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.scalatest.finders;

public class MethodFinder implements Finder {

    @Override
    public Selection find(AstNode node) {
      Selection result = null;
      while (result == null) {
        if (node instanceof MethodDefinition methodDef) {
          if (methodDef.parent() != null && methodDef.parent() instanceof ConstructorBlock && methodDef.paramTypes().length == 0 && methodDef.canBePartOfTestName()) {
            String displayName = NameTransformer.decode(methodDef.className()) + "." + methodDef.name();
            String testName = NameTransformer.encode(methodDef.name());
            result = new Selection(methodDef.className(), displayName, new String[] { testName });
          }
          else {
            if (node.parent() != null) 
              node = node.parent();
            else
              break;
          }
        }
        else {
          if (node.parent() != null) 
            node = node.parent();
          else
            break;
        }
      }
      return result;
    }
  
}
