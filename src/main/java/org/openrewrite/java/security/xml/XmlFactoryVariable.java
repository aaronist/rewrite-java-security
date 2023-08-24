package org.openrewrite.java.security.xml;

import lombok.Value;
import org.openrewrite.java.tree.J;

import java.util.List;

@Value
public class XmlFactoryVariable {
    String variableName;
    List<J.Modifier> modifiers;

    boolean isStatic() {
        return modifiers.stream().map(J.Modifier::getType).anyMatch(J.Modifier.Type.Static::equals);
    }
}
