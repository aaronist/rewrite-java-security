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
import org.openrewrite.analysis.InvocationMatcher;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.TypeUtils;

import java.util.Collections;

@AllArgsConstructor
@Getter
public abstract class XmlFactoryVisitor<P> extends JavaIsoVisitor<P> {
    static int count = 0;

    private final InvocationMatcher factoryInstance;

    private final String factoryFqn;

    private final String factoryInitializationMethod;
    private final String factoryVariableName;

    private final ExternalDTDAccumulator acc;

    @Override
    public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, P ctx) {
        J.MethodInvocation m = super.visitMethodInvocation(method, ctx);
        if (factoryInstance.matches(m)) {
            count++;
            addMessage(factoryInitializationMethod + count);

            J.VariableDeclarations.NamedVariable parentVariable = getCursor().firstEnclosing(J.VariableDeclarations.NamedVariable.class);
            Cursor maybeParentAssignment = getCursor().dropParentUntil(c -> c instanceof J.Assignment || c instanceof J.ClassDeclaration);
            if (parentVariable != null) {
                if (TypeUtils.isOfClassType(parentVariable.getType(), factoryFqn)) {
                    XmlFactoryVariable factoryVariable = new XmlFactoryVariable(
                            parentVariable.getSimpleName(),
                            getCursor().firstEnclosingOrThrow(J.VariableDeclarations.class).getModifiers()
                    );
                    addMessage(factoryVariableName + count, factoryVariable);
                }
            } else if (maybeParentAssignment.getValue() instanceof J.Assignment) {
                J.Assignment parentAssignment = maybeParentAssignment.getValue();
                if (TypeUtils.isOfClassType(parentAssignment.getVariable().getType(), factoryFqn)) {
                    if (parentAssignment.getVariable().unwrap() instanceof J.Identifier) {
                        J.Identifier ident = (J.Identifier) parentAssignment.getVariable().unwrap();
                        XmlFactoryVariable factoryVariable = new XmlFactoryVariable(
                                ident.getSimpleName(),
                                Collections.emptyList()
                        );
                        addMessage(factoryVariableName + count, factoryVariable);
                    }
                }
            }
        }
        return m;
    }

    /**
     * Adds a message/flag on the first enclosing class instance.
     *
     * @param message The message to be added.
     */
    protected void addMessage(String message) {
        addMessage(message, getCursor().dropParentUntil(J.Block.class::isInstance));
    }

    protected void addMessage(String message, Object value) {
        putMessageOnFirstEnclosingIfMissing(getCursor(), J.ClassDeclaration.class, message, value);
    }

    private static void putMessageOnFirstEnclosingIfMissing(Cursor c, Class<?> enclosing, String key, Object value) {
        if (enclosing.isInstance(c.getValue())) {
            if (c.getMessage(key) == null) {
                c.putMessage(key, value);
            }
        } else if (c.getParent() != null) {
            putMessageOnFirstEnclosingIfMissing(c.getParent(), enclosing, key, value);
        }
    }
}
