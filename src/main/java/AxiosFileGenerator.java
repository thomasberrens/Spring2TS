
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Parameter;
import java.lang.reflect.Type;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;


public class AxiosFileGenerator {

    private final RequestMappingHandlerMapping handlerMapping;

    private final List<String> defaultFunctions = new ArrayList<>();

    private final TypeScriptInterfaceGenerator typeScriptInterfaceGenerator;

    public AxiosFileGenerator(RequestMappingHandlerMapping handlerMapping, TypeScriptInterfaceGenerator typeScriptInterfaceGenerator) {
        this.handlerMapping = handlerMapping;
        this.typeScriptInterfaceGenerator = typeScriptInterfaceGenerator;

        addDefaultFunction("export const setDefaultHeader = (header: string, value: string) => axios.defaults.headers.common[header] = value;");
        addDefaultFunction("export const setBaseUrl = (url: string) => axios.defaults.baseURL = url;");
    }

    public List<String> getDefaultFunctions() {
        return defaultFunctions;
    }

    public void addDefaultFunction(String function) {
        defaultFunctions.add(function);
    }

    public void generateAxiosFile(String outputPath) {
        StringBuilder content = new StringBuilder();

        // Generate Axios functions and append to the content
        handlerMapping.getHandlerMethods().forEach((key, value) -> {
            String url = key.getPatternValues().toString().replace("[", "").replace("]", "");
            String methodName = value.getMethod().getName();
            Set<RequestMethod> httpMethods = key.getMethodsCondition().getMethods();
            String httpMethod = httpMethods.isEmpty() ? "GET" : httpMethods.iterator().next().name();

            Parameter[] parameters = value.getMethod().getParameters();

            // Generate the Axios function
            String axiosFunction = generateAxiosFunction(httpMethod, url, methodName, parameters, value.getMethod().getGenericReturnType());

            // Append the Axios function to the content
            content.append(axiosFunction).append("\n");
        });

        // Generate import statements and insert at the beginning of the content
        typeScriptInterfaceGenerator.getGeneratedInterfaces().forEach((key) -> {
            String importStatement = "import type { " + key + " } from './" + key + "';";
            content.insert(0, importStatement + "\n");
        });

        content.insert(0, "import axios from 'axios';\n");

        defaultFunctions.forEach((defaultFunction) -> {
            content.append(defaultFunction).append("\n");
        });

        // Write the content to the output file
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(outputPath))) {
            writer.write(content.toString());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    private String generateAxiosFunction(String httpMethod, String url, String methodName, Parameter[] parameters, Type returnType) {
        // Parse the URL to find path variables
        List<String> pathVariables = new ArrayList<>();
        Matcher matcher = Pattern.compile("\\{(.*?)}").matcher(url);
        while (matcher.find()) {
            pathVariables.add(matcher.group(1));
        }

        // Add path variables as arguments to the Axios function
        String args = pathVariables.stream()
                .map(var -> var + ": " + typeScriptInterfaceGenerator.javaTypeToTsType(Arrays.stream(parameters)
                        .filter(p ->  {
                            PathVariable pathVariable = p.getAnnotation(PathVariable.class);
                            if (pathVariable == null) return false;

                            return p.getName().equals(var) || pathVariable.value().equals(var) || pathVariable.name().equals(var);
                        })
                        .findFirst()
                        .orElseThrow(IllegalArgumentException::new)
                        .getType()).getTsType())
                .collect(Collectors.joining(", "));

        // Add request body as an argument to the Axios function
        String requestBodyArg = Arrays.stream(parameters)
                .filter(p -> p.getAnnotation(RequestBody.class) != null)
                .map(p -> p.getName() + ": " + typeScriptInterfaceGenerator.javaTypeToTsType(p.getParameterizedType()).getTsType())
                .findFirst()
                .orElse("");

        // Check if there is a Pageable parameter
        Optional<Parameter> pageableParam = Arrays.stream(parameters)
                .filter(p -> Pageable.class.isAssignableFrom(p.getType()))
                .findFirst();

        // If there is a Pageable parameter, add it to the args and append it to the URL as query parameters
        if (pageableParam.isPresent()) {
            if (!args.isEmpty())
                args += ", ";
            args += "pageable: { page: number, size: number, sort: string }";
            url += "?page={pageable.page}&size={pageable.size}&sort={pageable.sort}";
        }

        // Check if there are @RequestParam parameters
        List<Parameter> requestParamParams = Arrays.stream(parameters)
                .filter(p -> p.getAnnotation(RequestParam.class) != null)
                .toList();

        // If there are @RequestParam parameters, add them to the args and append them to the URL as query parameters
        for (Parameter param : requestParamParams) {
            RequestParam requestParam = param.getAnnotation(RequestParam.class);
            String paramName = requestParam.value().isEmpty() ? param.getName() : requestParam.value();
            // only add the comma if there are already arguments
            if (!args.isEmpty())
                args += ", ";

            args +=  paramName + ": string";
            url += (url.contains("?") ? "&" : "?") + paramName + "={" + paramName + "}";
        }

        if (!requestBodyArg.isEmpty()) {
            args = args.isEmpty() ? requestBodyArg : args + ", " + requestBodyArg;
        }

        // Replace path variables in the URL with JavaScript template literals
        String jsUrl = url.replaceAll("\\{(.*?)}", "\\${$1}");

        String requestBodyName = requestBodyArg.split(":")[0]; // Extract the name of the request body
        if (!requestBodyName.isEmpty())
            requestBodyName = requestBodyName.transform(name -> ", " + name);

        // Get the TypeScript type for the return type of the endpoint method
        String returnTypeTs = typeScriptInterfaceGenerator.javaTypeToTsType(returnType).getTsType();

        return "export const " + methodName + " = (" + args + "): Promise<" + returnTypeTs + "> => axios." + httpMethod.toLowerCase() + "(`" + jsUrl + "`" + requestBodyName + ").then(response => response.data).catch(error => { throw error });";
    }
}
