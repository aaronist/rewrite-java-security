/*
 * Copyright 2023 the original author or authors.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * https://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.openrewrite.java.security.xml;

import org.openrewrite.java.tree.J;

import java.util.Collections;
import java.util.Set;

public class TransformerFactoryInsertAttributeStatementVisitor<P> extends XmlFactoryInsertVisitor<P> {
    private static final Set<String> IMPORTS = Collections.singleton("javax.xml.XMLConstants");

    private final boolean needsExternalEntitiesDisabled;
    private final boolean needsStylesheetsDisabled;
    private final boolean needsFeatureSecureProcessing;

    public TransformerFactoryInsertAttributeStatementVisitor(
            J.Block scope,
            XmlFactoryVariable factoryVariable,
            boolean needsExternalEntitiesDisabled,
            boolean needsStylesheetsDisabled,
            boolean needsFeatureSecureProcessing
    ) {
        super(
                scope,
                factoryVariable,
                TransformerFactoryFixVisitor.TRANSFORMER_FACTORY_INSTANCE,
                TransformerFactoryFixVisitor.TRANSFORMER_FACTORY_SET_ATTRIBUTE,
                IMPORTS
        );

        this.needsExternalEntitiesDisabled = needsExternalEntitiesDisabled;
        this.needsStylesheetsDisabled = needsStylesheetsDisabled;
        this.needsFeatureSecureProcessing = needsFeatureSecureProcessing;
    }

    @Override
    public void updateTemplate() {
        if (needsExternalEntitiesDisabled) {
            getTemplate().append(getFactoryVariableName()).append(".setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, \"\");");
        }
        if (needsStylesheetsDisabled) {
            getTemplate().append(getFactoryVariableName()).append(".setAttribute(XMLConstants.ACCESS_EXTERNAL_STYLESHEET, \"\");");
        }
        if (needsFeatureSecureProcessing) {
            getTemplate().append(getFactoryVariableName()).append(".setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);");
        }
    }
}
