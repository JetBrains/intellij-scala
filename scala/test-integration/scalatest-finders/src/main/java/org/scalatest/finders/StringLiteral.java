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

public class StringLiteral implements AstNode {
  
  private final String className;
  private final AstNode parent;
  private final String value;
    
  public StringLiteral(String className, AstNode parent, String value) {
    this.className = className;
    this.parent = parent;
    if (parent != null)
      parent.addChild(this);
    this.value = value;
  }
  
  @Override
  public String className() {
    return className;
  }
  
  @Override
  public AstNode parent() {
    return parent;
  }
  
  @Override
  public AstNode[] children() {
    return new AstNode[0];
  }
  
  @Override
  public String name() {
    return "StringLiteral";
  }
  
  @Override
  public void addChild(AstNode node) {
    throw new UnsupportedOperationException("StringLiteral does not support addChild method.");  
  }

  @Override
  public boolean canBePartOfTestName() {
    return true;
  }

  public String value() {
    return value;
  }
  
  @Override
  public String toString() {
    return value;
  }
}
