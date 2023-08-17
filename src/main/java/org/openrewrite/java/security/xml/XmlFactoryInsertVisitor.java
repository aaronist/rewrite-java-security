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

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.openrewrite.Cursor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaCoordinates;
import org.openrewrite.java.tree.Statement;

import java.util.Set;

@AllArgsConstructor
@Getter
public abstract class XmlFactoryInsertVisitor<P> extends JavaIsoVisitor<P> {
    private final StringBuilder template = new StringBuilder();
    private final J.Block scope;
    private final String factoryVariableName;
    private final MethodMatcher factoryInstanceMatcher;
    private final MethodMatcher factoryMethodCallMatcher;

    public Statement getInsertStatement(J.Block b) {
        Statement beforeStatement = null;
        if (b.isScope(scope)) {
            for (int i = b.getStatements().size() - 2; i > -1; i--) {
                Statement st = b.getStatements().get(i);
                Statement stBefore = b.getStatements().get(i + 1);
                if (st instanceof J.MethodInvocation) {
                    J.MethodInvocation m = (J.MethodInvocation) st;
                    if (factoryInstanceMatcher.matches(m) || factoryMethodCallMatcher.matches(m)) {
                        beforeStatement = stBefore;
                    }
                } else if (st instanceof J.VariableDeclarations) {
                    J.VariableDeclarations vd = (J.VariableDeclarations) st;
                    if (vd.getVariables().get(0).getInitializer() instanceof J.MethodInvocation) {
                        J.MethodInvocation m = (J.MethodInvocation) vd.getVariables().get(0).getInitializer();
                        if (m != null && factoryInstanceMatcher.matches(m)) {
                            beforeStatement = stBefore;
                        }
                    }
                }
            }
        }
        return beforeStatement;
    }

    private JavaCoordinates getInsertCoordinates(J.Block b, Statement s) {
        return s != null ? s.getCoordinates().before() : b.getCoordinates().lastStatement();
    }

    public J.Block updateBlock(J.Block b, J.Block block, Statement beforeStatement, Set<String> imports) {
        if (getCursor().getParent() != null && getCursor().getParent().getValue() instanceof J.ClassDeclaration) {
            template.insert(0, "{\n").append("}");
        }
        b = JavaTemplate
                .builder(template.toString())
                .imports(imports.toArray(new String[0]))
                .contextSensitive()
                .build()
                .apply(new Cursor(getCursor().getParent(), b), getInsertCoordinates(b, beforeStatement));
        if (b != block) {
            imports.forEach(this::maybeAddImport);
        }
        return b;
    }
}
