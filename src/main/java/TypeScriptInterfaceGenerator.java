import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Transient;
import lombok.Getter;


import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

public class TypeScriptInterfaceGenerator {
    @Getter
    private final Set<String> generatedInterfaces = new HashSet<>();
    private final List<String> packagesToScan;

    public TypeScriptInterfaceGenerator(List<String> packagesToScan) {
        this.packagesToScan = packagesToScan;
    }

    private boolean shouldScanPackage(String packageName) {
        return packagesToScan.stream().anyMatch(packageName::startsWith);
    }


    public void generateTsInterface(Class<?> returnType) {
        if (generatedInterfaces.contains(returnType.getSimpleName())) {
            return;
        }

        generatedInterfaces.add(returnType.getSimpleName());

        StringBuilder tsInterface = new StringBuilder("export interface " + javaTypeToTsType(returnType).getTsType() + " {\n");

        for (Field field : returnType.getDeclaredFields()) {
            if (field.getAnnotation(JsonIgnore.class) != null || field.getAnnotation(Transient.class) != null) continue;

            TypeScriptType tsType = javaTypeToTsType(field.getGenericType());
            tsInterface.append("\t").append(field.getName()).append(": ").append(tsType.getTsType()).append(";\n");

            tsInterface.insert(0, tsType.generateImportStatement(returnType.getSimpleName(), packagesToScan));
        }

        tsInterface.append("}\n");

        Path filePath = Paths.get("./ts/" + returnType.getSimpleName() + ".ts");
        try {
            Files.createDirectories(filePath.getParent());
            Files.writeString(filePath, tsInterface.toString(), StandardCharsets.UTF_8);

        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private String getTsPrimitiveType(Class<?> classType) {
        if (classType == String.class) {
            return "string";
        } else if (classType == boolean.class || classType == Boolean.class) {
            return "boolean";
        } else if (classType.isPrimitive() || Number.class.isAssignableFrom(classType)) {
            if (classType == void.class) {
                return "void";
            }

            return "number";
        } else {
            return "any";
        }
    }

    public TypeScriptType javaTypeToTsType(Type type) {
        if (type instanceof Class<?> classType) {
            // Handle primitive types and their wrapper classes
            if (classType.isPrimitive() || classType == String.class || Number.class.isAssignableFrom(classType) || classType == Boolean.class) {
                return new TypeScriptType(Collections.singletonList(classType), getTsPrimitiveType(classType));
            }
            // Handle arrays
            else if (classType.isArray()) {
                TypeScriptType componentType = javaTypeToTsType(classType.getComponentType());
                return new TypeScriptType(Collections.singletonList(classType), "Array<" + componentType.getTsType() + ">");
            }
            // Handle enums
            else if (classType.isEnum()) {
                generateTsEnum(classType);
                return new TypeScriptType(Collections.singletonList(classType), classType.getSimpleName());
            }

            else {
                Package classPackage = classType.getPackage();

                if (classPackage != null && shouldScanPackage(classPackage.getName())) {

                    generateTsInterface(classType);
                    // check if classType has a generic type
                    if (classType.getTypeParameters().length > 0) {

                        // map the generic types to strings
                        List<String> genericTypes = Arrays.stream(classType.getTypeParameters()).map(TypeVariable::getName).toList();
                        final String genericTypeString = genericTypes.stream().reduce((s1, s2) -> s1 + ", " + s2).orElse("");


                        return new TypeScriptType(Collections.singletonList(classType), classType.getSimpleName() + "<" + genericTypeString + ">");
                    } else {
                        return new TypeScriptType(Collections.singletonList(classType), classType.getSimpleName());
                    }
                }
            }
        } else if (type instanceof ParameterizedType parameterizedType) {

            // check if the raw type is a map
            if(parameterizedType.getRawType() == Map.class) {

                String tsType = "Map<";
                final List<TypeScriptType> types = Arrays.stream(parameterizedType.getActualTypeArguments()).map(this::javaTypeToTsType).toList();

                final List<Type> javaTypes = new ArrayList<>(types.stream().flatMap(t -> t.getJavaTypes().stream()).toList());


                return getTypeScriptType(javaTypes, tsType, types);
            }

            if(parameterizedType.getRawType() == List.class || parameterizedType.getRawType() == Set.class || parameterizedType.getRawType() == Collection.class){

                final List<Type> javaTypes = new ArrayList<>();
                String tsType = "Array<";
                final List<TypeScriptType> types = Arrays.stream(parameterizedType.getActualTypeArguments()).map(this::javaTypeToTsType).toList();

                return getTypeScriptType(javaTypes, tsType, types);
            }

            if (shouldScanPackage(parameterizedType.getRawType().getTypeName())) {

                // create the interface for the parameterized type
                if (parameterizedType.getRawType() instanceof Class<?> rawClass) {
                    final List<Type> javaTypes = new ArrayList<>();
                    String tsType = rawClass.getSimpleName() +"<";
                    final List<TypeScriptType> types = Arrays.stream(parameterizedType.getActualTypeArguments()).map(this::javaTypeToTsType).toList();

                    return getTypeScriptType(javaTypes, tsType, types);
                }


            }
        }

        if (type instanceof TypeVariable<?> typeVariable)
            return new TypeScriptType(new ArrayList<>(), typeVariable.getName());

        return new TypeScriptType(new ArrayList<>(), "any");
    }

    private TypeScriptType getTypeScriptType(List<Type> javaTypes, String tsType, List<TypeScriptType> types) {
        javaTypes.addAll(types.stream().flatMap(t -> t.getJavaTypes().stream()).toList());

        for (TypeScriptType childType : types) {
            tsType += childType.getTsType() + ", ";
        }
        tsType = tsType.substring(0, tsType.length() - 2) + ">";

        return new TypeScriptType(javaTypes, tsType);
    }

    private void generateTsEnum(Class<?> enumClass) {
        if (generatedInterfaces.contains(enumClass.getSimpleName()))
            return;

        generatedInterfaces.add(enumClass.getSimpleName());

        StringBuilder tsEnum = new StringBuilder("export enum " + enumClass.getSimpleName() + " {\n");

        for (Object enumConstant : enumClass.getEnumConstants()) {
            tsEnum.append("\t").append(enumConstant.toString()).append(" = ").append("\"").append(enumConstant).append("\"").append(",\n");
        }

        tsEnum.append("}\n");

        Path filePath = Paths.get("./ts/" + enumClass.getSimpleName() + ".ts");
        try {
            Files.createDirectories(filePath.getParent());
            Files.writeString(filePath, tsEnum.toString(), StandardCharsets.UTF_8);

        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
