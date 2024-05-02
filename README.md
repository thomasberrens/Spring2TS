
---

# Spring2TS

Spring2TS is an open-source tool designed to streamline the workflow for full-stack developers working with Spring Boot and TypeScript. It automates the generation of TypeScript interfaces and Axios functions based on your Spring endpoints, significantly reducing the time and effort required to maintain synchronization between front-end and back-end codebases.

## Features
* **Automatic Generation of TypeScript Interfaces**: Converts your Spring entities and DTOs into TypeScript interfaces.
* **Extensive Generic Type Support**
* **Enum Support**
* **Supports Various Annotations**: Including Pageable, @JsonIgnore, @Transient, @RequestBody, @PathVariable, @RequestParam.
* **Axios Integration**: Generates ready-to-use Axios functions for seamless API calls.
* **Easy Setup**: Quick integration into existing Spring and TypeScript projects.

## Examples

### Spring Endpoints

```java
@PostMapping("/login")
public AuthResponse login(@RequestBody LoginDTO loginDTO) {

    return authService.login(loginDTO.getUsername(), loginDTO.getPassword());
}


@GetMapping("/info")
public User getUserInfo() {
    return userService.getUserByUsername(SecurityContextHolder.getContext().getAuthentication().getPrincipal().toString()).orElseThrow();
}


@PostMapping("/reset-password/{id}")
public void confirmResetPassword(@RequestBody ResetPasswordDTO resetPassword, @PathVariable("id") String id) {
    final ForgotPasswordRequest forgotPasswordRequest = passwordResetTask.getRequest(id);

    if (forgotPasswordRequest == null) return;

    final String email = forgotPasswordRequest.getEmail();

    final User user = userService.getUserByUsername(email).orElseThrow();

    passwordResetTask.removeRequest(id);

    userService.updateUserPassword(user, resetPassword.getPassword());
}
```

## The Spring Endpoins becomes the following:
### Axios Functions

```typescript
import axios from 'axios';
import type { User } from './User';
import type { AuthResponse } from './AuthResponse';
import type { LoginDTO } from './LoginDTO';
import type { ResetPasswordDTO } from './ResetPasswordDTO';

export const login = (loginDTO: LoginDTO): Promise<AuthResponse> => axios.post(`/user/login`, loginDTO).then(response => response.data).catch(error => { throw error });
export const getUserInfo = (): Promise<User> => axios.get(`/user/info`).then(response => response.data).catch(error => { throw error });
export const confirmResetPassword = (id: string, resetPassword: ResetPasswordDTO): Promise<void> => axios.post(`/user/reset-password/${id}`, resetPassword).then(response => response.data).catch(error => { throw error });

export const setDefaultHeader = (header: string, value: string) => axios.defaults.headers.common[header] = value;
export const setBaseUrl = (url: string) => axios.defaults.baseURL = url;
```

### TypeScript Interfaces

```typescript
export interface AuthResponse {
	authenticationToken: string;
}

import type { RoleType } from './RoleType';
export interface User {
	username: string;
	roles: Array<RoleType>;
}

export interface ResetPasswordDTO {
	password: string;
}

export interface LoginDTO {
	username: string;
	password: string;
}

export enum RoleType {
	ADMIN = "ADMIN",
	USER = "USER",
}
```

## Dependencies
* **Spring (backend)**
* **TypeScript (frontend)**
* **Axios (frontend)**

## Getting Started

These instructions will get you a copy of the project up and running on your local machine for development and testing purposes.

### Maven Setup

To set up with Maven, follow these steps:

1. Add the Maven repository to your `pom.xml`:

    ```xml
    <repository>
        <id>github</id>
        <name>GitHub thomasberrens Apache Maven Packages</name>
        <url>https://maven.pkg.github.com/thomasberrens/Spring2TS</url>
    </repository>
    ```

2. Add the dependency:

    ```xml
    <dependency>
        <groupId>nl.thomasberrens</groupId>
        <artifactId>spring2ts</artifactId>
        <version>1.0</version>
    </dependency>
    ```

   Alternatively, clone the project and copy-paste it into your project.

### Gradle Setup

To set up with Gradle, follow these steps:

1. Add the Maven repository to your `build.gradle`:

    ```gradle
    repositories {
        maven {
            url "https://maven.pkg.github.com/thomasberrens/Spring2TS"
            credentials {
                username = project.findProperty("gpr.user") ?: System.getenv("USERNAME")
                password = project.findProperty("gpr.token") ?: System.getenv("TOKEN")
            }
        }
    }
    ```

2. Add the dependency:

    ```gradle
    dependencies {
        implementation 'nl.thomasberrens:spring2ts:1.0'
    }
    ```

   Alternatively, clone the project and copy-paste it into your project.

### Spring Setup

To set up with Spring, add the following code to your main application class:

```java
@Bean
public CommandLineRunner start(final RequestMappingHandlerMapping requestMappingHandlerMapping) {
    return (args -> {
        final Spring2TSModule spring2TSModule = new Spring2TSModule(List.of("nl.thomasberrens"), "ts/");
        spring2TSModule.enableGitModule("https://github.com/thomasberrens/XXX.git", "username", "git token");
        spring2TSModule.generate(requestMappingHandlerMapping);
    });
}
```

Ensure to replace `"nl.thomasberrens"` with your package name. Also, consider adding the `ts/` directory to your `.gitignore`.

### Additional Information
* **Portfolio**: [Thomas Berrens - Portfolio](https://www.thomasberrens.dev/)
* **LinkedIn**: [Thomas Berrens on LinkedIn](https://www.linkedin.com/in/thomas-berrens-4698141a4/)
* **Twitter**: [Thomas Berrens on Twitter](https://twitter.com/thomas_berrens)

---

This version includes the examples and "Becomes" sections back into the README while maintaining improved formatting and organization for readability.