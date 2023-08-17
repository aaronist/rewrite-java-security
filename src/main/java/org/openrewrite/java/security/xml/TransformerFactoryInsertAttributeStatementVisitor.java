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
import org.openrewrite.java.tree.Statement;

import java.util.Collections;

public class TransformerFactoryInsertAttributeStatementVisitor<P> extends XmlFactoryInsertVisitor<P> {
    public TransformerFactoryInsertAttributeStatementVisitor(
            J.Block scope,
            String factoryVariableName,
            boolean needsExternalEntitiesDisabled,
            boolean needsStylesheetsDisabled,
            boolean needsFeatureSecureProcessing
    ) {
        super(
                scope,
                factoryVariableName,
                TransformerFactoryFixVisitor.TRANSFORMER_FACTORY_INSTANCE,
                TransformerFactoryFixVisitor.TRANSFORMER_FACTORY_SET_ATTRIBUTE
        );

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

    @Override
    public J.Block visitBlock(J.Block block, P ctx) {
        J.Block b = super.visitBlock(block, ctx);
        Statement beforeStatement = getInsertStatement(b);
        if (b.isScope(getScope())) {
            b = updateBlock(b, block, beforeStatement, Collections.singleton("javax.xml.XMLConstants"));
        }
        return b;
    }
}
