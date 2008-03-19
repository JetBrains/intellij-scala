/*
 * Copyright 2000-2007 JetBrains s.r.o.
 *
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

package org.jetbrains.plugins.scala.error;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.util.DefaultJDOMExternalizer;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.JDOMExternalizable;
import com.intellij.openapi.util.WriteExternalException;
import org.apache.commons.codec.binary.Base64;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;

/**
 * Created by IntelliJ IDEA.
 *
 * @author: oleg
 * @date: Feb 26, 2007
 */

/**
 * This is just a a  bit modified copy of com.intellij.diagnostic.ErrorReportConfigurable
 */
public class ErrorReportConfigurable implements JDOMExternalizable, ApplicationComponent {
  public String JIRA_LOGIN = "";
  public String JIRA_PASSWORD_CRYPT = "";
  public boolean KEEP_JIRA_PASSWORD = false;

  public static ErrorReportConfigurable getInstance() {
    return ApplicationManager.getApplication().getComponent(ErrorReportConfigurable.class);
  }

  public void readExternal(Element element) throws InvalidDataException {
    DefaultJDOMExternalizer.readExternal(this, element);
    if (!KEEP_JIRA_PASSWORD){
      JIRA_PASSWORD_CRYPT = "";
    }
  }

  public void writeExternal(Element element) throws WriteExternalException {
    String itnPassword = JIRA_PASSWORD_CRYPT;
    if (!KEEP_JIRA_PASSWORD){
      JIRA_PASSWORD_CRYPT = "";
    }
    DefaultJDOMExternalizer.writeExternal(this, element);
    JIRA_PASSWORD_CRYPT = itnPassword;
  }

  @NotNull
  public String getComponentName() {
    return "Error Report Configurable";
  }

  public void initComponent() { }

  public void disposeComponent() {
  }

  public String getPlainJiraPassword() {
    return new String(new Base64().decode(ErrorReportConfigurable.getInstance().JIRA_PASSWORD_CRYPT.getBytes()));
  }

  public void setPlainJiraPassword(final String password) {
    JIRA_PASSWORD_CRYPT = new String(new Base64().encode(password.getBytes()));
  }
}
