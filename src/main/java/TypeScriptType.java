import lombok.Getter;

import java.lang.reflect.Type;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Getter
public class TypeScriptType {
    private final List<Type> javaTypes;
    private final String tsType;

    public TypeScriptType(List<Type> javaTypes, String tsType) {
        this.javaTypes = javaTypes;
        this.tsType = tsType;
    }

    private boolean shouldScanPackage(List<String> packagesToScan, String packageName) {
        return packagesToScan.stream().anyMatch(packageName::startsWith);
    }

    public String generateImportStatement(String currentClassName, List<String> packagesToScan) {
        StringBuilder importStatements = new StringBuilder();
        Set<String> importedTypes = new HashSet<>();
        for (Type javaType : javaTypes) {
            if (javaType instanceof Class<?> classType) {
                String typeName = classType.getSimpleName();
                if (!importedTypes.contains(typeName) && !typeName.equals(currentClassName)) {
                    Package classPackage = classType.getPackage();
                    if (classPackage != null && shouldScanPackage(packagesToScan, classPackage.getName())) {
                        importStatements.append("import type {").append(typeName).append("} from './").append(typeName).append("';\n");

                        importedTypes.add(typeName);
                    }
                }
            }
        }
        return importStatements.toString();
    }
}

