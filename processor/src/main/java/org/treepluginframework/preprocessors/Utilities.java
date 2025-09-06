package org.treepluginframework.preprocessors;

import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import java.util.stream.Collectors;

public class Utilities {
    public static String toRuntimeClassName(TypeElement typeElement, Elements elementUtils) {
        String packageName = elementUtils.getPackageOf(typeElement).getQualifiedName().toString();
        String fullQualifiedName = typeElement.getQualifiedName().toString();

        if (packageName.isEmpty()) {
            // Default package, rare case
            return fullQualifiedName.replace('.', '$');
        }

        // Get class path portion (relative to package)
        String classPath = fullQualifiedName.substring(packageName.length() + 1); // +1 to skip dot
        classPath = classPath.replace('.', '$'); // Replace inner class dots with $

        return packageName + "." + classPath;
    }

    public static String getQualifiedTypeName(TypeMirror type, Types typeUtils) {
        switch (type.getKind()) {
            case DECLARED -> {
                // Regular object type
                TypeElement typeElement = (TypeElement) ((DeclaredType) type).asElement();
                return typeElement.getQualifiedName().toString();
            }
            case ARRAY -> {
                // Handle array types recursively
                ArrayType arrayType = (ArrayType) type;
                return getQualifiedTypeName(arrayType.getComponentType(), typeUtils) + "[]";
            }
            default -> {
                return type.toString();
                /*
                if (type.getKind().isPrimitive()) {
                    // Use boxed type if needed
                    TypeElement boxed = typeUtils.boxedClass((PrimitiveType) type);
                    return boxed.getQualifiedName().toString(); // use `type.toString()` if you want raw primitive name
                } else {
                    return type.toString(); // fallback (wildcards, type vars, etc.)
                }
                */
            }
        }
    }

    public static String createConstructorSignature(ExecutableElement constructor){
        String constructorSig = constructor.getParameters().stream()
                .map(param -> param.asType().toString())
                .collect(Collectors.joining(",", "[", "]"));
        return constructorSig;
    }

}
