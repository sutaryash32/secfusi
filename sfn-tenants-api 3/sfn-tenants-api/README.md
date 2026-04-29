# Secufusion Backend Platform

Secufusion is a multi-tenant, microservices-based backend platform that provides authentication, tenant management, policy management, event tracking, and feature/package configuration for MSSP customers.  
This document is intended for client reviewers, auditors, and developers who need to understand the system and run it locally end-to-end.



## 1. System Overview

Secufusion follows a microservices architecture built with **Spring Boot 3.2.x**, **Java 17**, and **PostgreSQL 15**, exposing REST APIs secured via a dedicated authentication and IAM layer.  
Services communicate primarily over HTTP, with API Gateway as the unified entry point and Eureka for service discovery.

**Key characteristics:**

- **Microservices**: Independently deployable Spring Boot services (auth, IAM, tenants, policies, etc.).
- **Service Discovery**: Eureka Server used for dynamic registration and lookup of services. 
- **API Gateway**: Single external entry point routing to internal services and aggregating Swagger/API docs. 
- **Data Layer**: **PostgreSQL 15** as primary datastore; test/development may use local Postgres. 

**Technology Stack:**

| Component | Technology | Version |
|-----------|------------|---------|
| Language  | OpenJDK 	 | **17**  |
| Framework | Spring Boot| **3.2.x** |
| Database  | PostgreSQL | **15** |
| Service Discovery |  Eureka | 
| ORM | Spring Data JPA / Hibernate | **6.4.x** | 
| API Docs | OpenAPI 3 / Swagger UI | **3.x** | 



## 2. Microservices and Responsibilities

The Secufusion platform consists of the following backend microservices.

> Note: Ports are indicative and should be aligned with each service's `application.properties` or `application.yml` in the repository.

| # | Service Name       | Suggested Port | Responsibility                                                                 |
|---|--------------------|---------------|---------------------------------------------------------------------------------|
| 1 | gateway-service    | 8083          | API Gateway entry point, routing, rate limiting, central Swagger aggregation    |
| 2 | iam-service        | 8084          | Identity and Access Management: users, roles, permissions, mappings             |
| 3 | tenants-service    | 8085          | Tenant onboarding, lifecycle management, configuration                          |
| 4 | events-service     | 8086          | Events and audit logging for tenant and user activities                         |
| 5 | auth-service       | 8087          | Issue/validate tokens, login, refresh, basic auth endpoints                     |
| 6 | policy-service     | 8089          | Policy management: browser policies, DLP, watermarking, homepage settings       |
| 7 | eureka-service     | 8761          | Service discovery registry for all microservices                                | 




Each service has its own Spring Boot application, dependencies (Maven `pom.xml`), configuration, and database schema (or schema segment).


## 3. Code Access (Azure DevOps)

Source control is hosted in **Azure DevOps** under the Secufusion organization.

### 3.1. Access via Azure DevOps 

1. Navigate to:  
   https://dev.azure.com/secufusion/SFCloud-MSSP/_git/sfn-events-api  
2. click on Repos in left menu.
2. Open the required repository (for example, `sfn-events-api`).  
3. Select develop branch. 

### 3.2. Clone Repository into Local via HTTPS

> Example for one microservice repository; repeat for each repo as instructed.

1. In Azure DevOps, open the repository (e.g., `sfn-events-api`).  
2. Click **Clone** → choose **HTTPS**.
3. Copy the provided clone URL.  
4. On your local machine:


**Authentication**: Use your Azure DevOps credentials or Personal Access Token (PAT).

**Repository Pattern**: `sfn-<service-name>-api` (e.g., `sfn-policy-api`, `sfn-iam-api`)



## 4. Prerequisites and Tooling

To build and run all microservices locally, install the following **exact versions**:


### 4.1. Required Tools

| Tool 			  | Exact Version 	 | Verification Command 
|------			  |----------------- |---------------------	
| **Java JDK**    | **17.0.9+**      | `java --version`          
| **PostgreSQL**  | **15.5**         | `psql --version`     
| **IntelliJ IDEA | 2025.2+**        | Recommended IDE |
| **Postman** REST API testing |

**Verify installations:**





## 5. Local Setup – Step by Step

### 5.1. Step 1 – Install JDK 17 and Configure Environment


**Linux/macOS:**

Download & extract JDK 17.0.9+
wget https://github.com/adoptium/temurin17-binaries/releases/download/jdk-17.0.9%2B9/OpenJDK17U-jdk_x64_linux_hotspot_17.0.9_9.tar.gz
tar -xzf OpenJDK17U-jdk_x64_linux_hotspot_17.0.9_9.tar.gz
export JAVA_HOME=$PWD/jdk-17.0.9+9
export PATH=$JAVA_HOME/bin:$PATH

**Windows :**

Set JAVA_HOME to JDK 17.0.9+ 
$env:JAVA_HOME="C:\Program Files\Eclipse Adoptium\jdk-17.0.9.9-hotspot"  
$env:Path="$env:JAVA_HOME\bin;$env:Path" 

Linux/macOS
wget https://downloads.apache.org/maven/maven-3/3.9.6/binaries/apache-maven-3.9.6-bin.tar.gz
tar -xzf apache-maven-3.9.6-bin.tar.gz
export PATH=$PWD/apache-maven-3.9.6/bin:$PATH


Verify
mvn --version # Apache Maven 3.9.6

### 5.3. Step 3 – Clone All Required Repositories

Create workspace
mkdir secufusion-backend && cd secufusion-backend

Clone each service (adjust repo names as per Azure DevOps)
git clone https://dev.azure.com/secufusion/SFCloud-MSSP/_git/sfn-iam-service
git clone https://dev.azure.com/secufusion/SFCloud-MSSP/_git/sfn-policy-service

... repeat for all services

Switch to develop branch
cd */ && git checkout develop branch



### 5.4. Step 4 – Install PostgreSQL 15.5




**Local Installation**

Ubuntu/Debian
sudo apt update
sudo apt install postgresql-15

macOS (Homebrew)
brew install postgresql@15
brew services start postgresql@15

Windows: Download from postgresql.org



**Create Databases:**

Connect to Postgres
psql -U secu_user -h localhost -d postgres

CREATE DATABASE 






## 6. Build & Run – All Services

### 6.1. Build All Services 

1. Navigate to Workspace Root
	cd secufusion-backend
	ls -la

	From secufusion-backend root
	for dir in */; do
	echo "Building $dir..."
	cd $dir
	mvn clean install -DskipTests
	cd ..
	
2. Go to Edit configurations.
3. Click on Add new configurations then select Application config the Main class.
4. Add the externalized .properties file path in Environment Variables. 
5. Run the application. repeat for all the microservices.




### 6.2. Startup Sequence (Critical Order)

Open **separate terminals** for each service:

Terminal 1: Eureka Service Discovery (30s startup)
cd eureka-service
mvn spring-boot:run -Dspring-boot.run.profiles=local

Terminal 2: IAM Service (depends on Eureka)
cd ../iam-service
mvn spring-boot:run -Dspring-boot.run.profiles=local

Terminal 3: Core Services
cd ../policy-service && mvn spring-boot:run -Dspring-boot.run.profiles=local
cd ../tenants-service && mvn spring-boot:run -Dspring-boot.run.profiles=local

Terminal 4-N: Remaining services + Gateway last
cd ../gateway-service && mvn spring-boot:run -Dspring-boot.run.profiles=local


### 6.3. Verification Endpoints

| Service | Health Check | Expected Response |
|---------|--------------|-------------------|
| Eureka | http://localhost:8761` | Dashboard with registered services |
| Gateway | `http://localhost:8083/actuator/health` | `{"status":"UP"}` |
| Policy | `http://localhost:8089/actuator/health` | `{"status":"UP"}` |
| IAM   | `http://localhost:8084/actuator/health`| `{"status":"UP"}` |

---

## 7. API Documentation (Swagger / OpenAPI)

**Primary Entry Point**: `http://localhost:8083/swagger-ui/index.html` (Gateway)
**Individual Services:**


IAM: http://localhost:8081/swagger-ui/index.html
Policy: http://localhost:8083/swagger-ui/index.html
Tenants: http://localhost:8090/swagger-ui/index.html





## 8. Environments, Profiles, and Configuration

**Spring Profiles Available:**
- `local` – Development (H2/Postgres, security relaxed)
- `dev` – Integration testing  
- `prod` – Production (not for local use)


**Run with profile:**


mvn spring-boot:run -Dspring-boot.run.profiles=local




## 9. Troubleshooting & Support

| Issue | Resolution |
|-------|------------|
| `JAVA_HOME not set` | `export JAVA_HOME=/path/to/jdk-17.0.9` |
| `Maven 3.9.6 required` | Download exact version from apache.org |
| `Port 8083 in use` | `lsof -ti:8083 | xargs kill -9` |
| `Postgres connection refused` | Verify `docker ps` or local service |
| `Eureka registration failed` | Start Eureka first (port 8761) |
