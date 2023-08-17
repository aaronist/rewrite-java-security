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

import org.openrewrite.Cursor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaCoordinates;
import org.openrewrite.java.tree.Statement;

public class TransformerFactoryInsertAttributeStatementVisitor<P> extends JavaIsoVisitor<P> {
    private final J.Block scope;
    private final StringBuilder attributeTemplate = new StringBuilder();
    private final String transformerFactoryVariableName;

    public TransformerFactoryInsertAttributeStatementVisitor(
            J.Block scope,
            String factoryVariableName,
            boolean needsExternalEntitiesDisabled,
            boolean needsStylesheetsDisabled,
            boolean needsFeatureSecureProcessing
    ) {
        this.scope = scope;
        this.transformerFactoryVariableName = factoryVariableName;

        if (needsExternalEntitiesDisabled) {
            attributeTemplate.append(transformerFactoryVariableName).append(".setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, \"\");");
        }
        if (needsStylesheetsDisabled) {
            attributeTemplate.append(transformerFactoryVariableName).append(".setAttribute(XMLConstants.ACCESS_EXTERNAL_STYLESHEET, \"\");");
        }
        if (needsFeatureSecureProcessing) {
            attributeTemplate.append(transformerFactoryVariableName).append(".setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);");
        }
    }

    @Override
    public J.Block visitBlock(J.Block block, P ctx) {
        J.Block b = super.visitBlock(block, ctx);
        Statement beforeStatement = null;
        if (b.isScope(scope)) {
            for (int i = b.getStatements().size() - 2; i > -1; i--) {
                Statement st = b.getStatements().get(i);
                Statement stBefore = b.getStatements().get(i + 1);
                if (st instanceof J.MethodInvocation) {
                    J.MethodInvocation m = (J.MethodInvocation) st;
                    if (TransformerFactoryFixVisitor.TRANSFORMER_FACTORY_INSTANCE.matches(m) || TransformerFactoryFixVisitor.TRANSFORMER_FACTORY_SET_ATTRIBUTE.matches(m)) {
                        beforeStatement = stBefore;
                    }
                } else if (st instanceof J.VariableDeclarations) {
                    J.VariableDeclarations vd = (J.VariableDeclarations) st;
                    if (vd.getVariables().get(0).getInitializer() instanceof J.MethodInvocation) {
                        J.MethodInvocation m = (J.MethodInvocation) vd.getVariables().get(0).getInitializer();
                        if (m != null && TransformerFactoryFixVisitor.TRANSFORMER_FACTORY_INSTANCE.matches(m)) {
                            beforeStatement = stBefore;
                        }
                    }
                }
            }

            if (getCursor().getParent() != null && getCursor().getParent().getValue() instanceof J.ClassDeclaration) {
                attributeTemplate.insert(0, "{").append("}");
            }
            JavaCoordinates insertCoordinates = beforeStatement != null ?
                    beforeStatement.getCoordinates().before() :
                    b.getCoordinates().lastStatement();
            b = JavaTemplate
                    .builder(attributeTemplate.toString())
                    .imports("javax.xml.XMLConstants")
                    .contextSensitive()
                    .build()
                    .apply(new Cursor(getCursor().getParent(), b), insertCoordinates);
            maybeAddImport("javax.xml.XMLConstants");
        }
        return b;
    }
}
