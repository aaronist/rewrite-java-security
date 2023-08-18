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
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.TypeUtils;

@AllArgsConstructor
@Getter
public abstract class XmlFactoryVisitor<P> extends JavaIsoVisitor<P> {
    private final MethodMatcher FACTORY_INSTANCE;

    private final String FACTORY_FQN;

    private final String FACTORY_INITIALIZATION_METHOD;
    private final String FACTORY_VARIABLE_NAME;

    private final ExternalDTDAccumulator acc;

    @Override
    public J.VariableDeclarations.NamedVariable visitVariable(J.VariableDeclarations.NamedVariable variable, P ctx) {
        J.VariableDeclarations.NamedVariable v = super.visitVariable(variable, ctx);
        if (TypeUtils.isOfClassType(v.getType(), FACTORY_FQN)) {
            getCursor().putMessageOnFirstEnclosing(J.ClassDeclaration.class, FACTORY_VARIABLE_NAME, v.getSimpleName());
        }
        return v;
    }

    @Override
    public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, P ctx) {
        J.MethodInvocation m = super.visitMethodInvocation(method, ctx);
        if (FACTORY_INSTANCE.matches(m)) {
            getCursor().putMessageOnFirstEnclosing(J.ClassDeclaration.class, FACTORY_INITIALIZATION_METHOD, getCursor().dropParentUntil(J.Block.class::isInstance));
        }
        return m;
    }

    /**
     * Adds a message/flag on the first enclosing class instance.
     *
     * @param message The message to be added.
     */
    public void addMessage(String message) {
        getCursor().putMessageOnFirstEnclosing(J.ClassDeclaration.class, message, getCursor().dropParentUntil(J.Block.class::isInstance));
    }
}
