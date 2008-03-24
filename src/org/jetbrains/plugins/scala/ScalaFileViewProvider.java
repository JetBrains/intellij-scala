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

package org.jetbrains.plugins.scala;

import com.intellij.lang.Language;
import com.intellij.lang.StdLanguages;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.SingleRootFileViewProvider;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.scala.util.ScalaToolsFactory;

/**
 * @author ilyas
*/
class ScalaFileViewProvider extends SingleRootFileViewProvider {

  public ScalaFileViewProvider(PsiManager manager, VirtualFile file, boolean physical) {
    super(manager, file, physical);
    myJavaRoot = ScalaToolsFactory.getInstance().createJavaView(this);
  }

  @Nullable
    protected PsiFile getPsiInner(Language target) {
    if (target == StdLanguages.JAVA) return myJavaRoot;
    return super.getPsiInner(target);
  }

  PsiFile myJavaRoot;

  public PsiElement findElementAt(int offset, Language language) {
    if (language == StdLanguages.JAVA) return myJavaRoot.findElementAt(offset);
    return super.findElementAt(offset, language);
  }
}
