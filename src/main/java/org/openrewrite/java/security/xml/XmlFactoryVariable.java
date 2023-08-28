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

import lombok.Value;
import org.openrewrite.analysis.util.FlagUtil;
import org.openrewrite.java.tree.Flag;
import org.openrewrite.java.tree.J;

import java.util.Collection;

@Value
public class XmlFactoryVariable {
    String variableName;
    Collection<Flag> flags;

    boolean isStatic() {
        return flags.stream().anyMatch(Flag.Static::equals);
    }

    static XmlFactoryVariable from(String variableName, Collection<J.Modifier> modifiers) {
        return new XmlFactoryVariable(
                variableName,
                FlagUtil.fromModifiers(modifiers)
        );
    }
}
