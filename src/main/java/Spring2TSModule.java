import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.WildcardType;
import java.util.Arrays;
import java.util.List;

public class Spring2TSModule {
    private final List<String> packagesToScan;
    private final String outputPath;
    private GitHandler gitHandler;
    private final TypeScriptInterfaceGenerator typeScriptInterfaceGenerator;

    public Spring2TSModule(final List<String> packagesToScan, String outputPath) {
        this.packagesToScan = packagesToScan;
        if (outputPath == null) throw new IllegalArgumentException("Output path cannot be null");
        if (!outputPath.endsWith("/")) outputPath += '/';
        this.outputPath = outputPath;
        this.typeScriptInterfaceGenerator = new TypeScriptInterfaceGenerator(packagesToScan);
    }

    public void enableGitModule(final String gitUrl, final String username, final String token) {
        gitHandler = new GitHandler(gitUrl, username, token, outputPath);
    }

    private void handleType(Type type) {
        if (type instanceof ParameterizedType parameterizedType) {
            Arrays.stream(parameterizedType.getActualTypeArguments()).forEach(this::handleType);

            if (parameterizedType.getRawType() instanceof Class<?> rawTypeClass) {
                final String packageName = rawTypeClass.getPackageName();
                if (shouldScanPackage(packageName))
                    typeScriptInterfaceGenerator.generateTsInterface(rawTypeClass);
            }
        } else if (type instanceof WildcardType wildcardType) {
            Arrays.stream(wildcardType.getUpperBounds()).forEach(this::handleType);
        } else if (type instanceof Class<?> actualClass) {
            final String packageName = actualClass.getPackageName();
            if (shouldScanPackage(packageName))
                typeScriptInterfaceGenerator.generateTsInterface(actualClass);

        }
    }

    private boolean shouldScanPackage(String packageName) {
        return packagesToScan.stream().anyMatch(packageName::startsWith);
    }

    public void generate(RequestMappingHandlerMapping mappingHandlerMapping) {
        mappingHandlerMapping.getHandlerMethods().forEach((key, value) -> {
            handleType(value.getMethod().getGenericReturnType());

            Arrays.stream(value.getMethodParameters()).forEach(parameter -> {
                Class<?> parameterType = parameter.getParameterType();
                final String packageName = parameterType.getPackage().getName();

                if (shouldScanPackage(packageName))
                    typeScriptInterfaceGenerator.generateTsInterface(parameterType);
            });
        });

        final AxiosFileGenerator axiosFileGenerator = new AxiosFileGenerator(mappingHandlerMapping, typeScriptInterfaceGenerator);
        axiosFileGenerator.generateAxiosFile(outputPath + "api.ts");

        if (gitHandler == null) return;

        gitHandler.addChanges();
        gitHandler.commitChanges("Updated typescript interfaces");
        gitHandler.pushChanges();
    }


}
