<!--
   Copyright 2021 Chitral Verma

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
-->

Examples
====================
Maven build tool allows a lot of flexibility to end users to model their complex
use cases.

Some specific use cases are described in the following sections. Resources for
these examples can be found at
[this link](https://github.com/chitralverma/jinja-maven-plugin/tree/master/examples/src/main/resources)
.

## Simple Substitution

This example uses a single value file for substitution into the JSON template
file.

* **Use case:** This is a simple use case of direct value substitution into the
  template file.


* **Template File:**

  ```json
  {
    "app_name": "{{name}}",
    "app_version": {{version}},
    "env_name": "{{env}}",
    "db_connect": "{{db}}"
  }
  ```

* **Value File:**

  ```json
  {
    "name": "SimpleApp",
    "version": 1.0,
    "env": "uat",
    "db_host": "1.2.3.4",
    "db_port": 9876,
    "db": "jdbc:mysql://{{db_host}}:{{db_port}}/"
  }
  ```

* **Output File:**

  ```json
  {
    "app_name": "SimpleApp",
    "app_version": 1.0,
    "env_name": "uat",
    "db_connect": "jdbc:mysql://1.2.3.4:9876/"
  }
  ```

## Complex Substitutions and Statements

This example uses multiple value files for direct and indirect substitutions
into the YAML template file along with complex conditional statements.

See [this link](https://jinja.palletsprojects.com/en/2.11.x/templates/#list-of-control-structures)
for more examples of Jinja Control Structures.

* **Use case:** This is a rather complex use case, through which some of the
  capabilities of Jinja templating engine and the plugin are shown as mentioned
  below,

    * Multilevel substitution

    * Value overriding

    * Multiple contexts

    * Control Structure support


* **Template File:**

  ```yaml
  server:
    ports: {{serverPorts}}
    context-path: {{contextPath}}
  config:
    app:
      name: {{name}}
    task:
      scheduling:
        pool:
          size: {{poolSize}}
    datasource:
      driverClassName: {{dbDriverClass}}
      url: {{connectString}}
      username: {{dbUserName}}
      password: {{dbPassword}}
      options: {{sourceOptions}}
  ```

* **Value Files:**

    * Common Properties for some sample system

        ```json
        {
          "name": "ComplexApp",
          "contextPath": "/v1",
          "poolSize": 7,
          "dbDriverClass": "com.mysql.jdbc.Driver",
          "mysql": "mysql",
          "db2": "db2",
          "dbType": "{% if 'mysql' in dbDriverClass %}mysql{% else %}db2{% endif %}",
          "connectString": "jdbc:{{dbType}}://{{db_host}}:{{db_port}}/",
          "sourceOptions": {
            "prepStmtCacheSize": 250,
            "cachePrepStmts": true
          }
        }
        ```
    * Common Properties for some sample system

      ```json
      {
        "serverPorts": [
          4000,
          4001,
          4002
        ],
        "db_host": "1.2.3.4",
        "db_port": "3306",
        "dbUserName": "db_user_uat",
        "dbPassword": "Password_uat_1234"
      }
      ```

* **Output File:**

  ```yaml
  server:
    ports: [4000,4001,4002]
    context-path: /v1
  config:
    app:
      name: ComplexApp
    task:
      scheduling:
        pool:
          size: 7
    datasource:
      driverClassName: com.mysql.jdbc.Driver
      url: jdbc:mysql://1.2.3.4:3306/
      username: db_user_uat
      password: Password_uat_1234
      options: {"prepStmtCacheSize":250,"cachePrepStmts":true}
  ```

## Rendering Profiles

This example is more about the flexibility of how and when the output files are
rendered.

* **Use case:** For this example we will assume a configuration driven
  application `TestApp` that runs in two environments `uat` and `prod`.

  The values present in the configurations for both these environments would be
  different and specific to the environment they are running in. Consider the
  following scenarios that may rise,

    1. Rendering everything in one pass: When you want all outputs to be
       rendered at the same time. Like, rendering the configuration for all
       environments (`uat` and `prod`) at the same time.
    2. Rendering selectively by triggers: When you want to conditionally render
       selected outputs. Like, rendering when certain condition(s) are met.

  For such scenarios, this plugin can be coupled with maven profiles to
  selectively render resources.


* **Template File:**

  `/path/to/template/a_template.j2`

  ```json
  {
    "app_name": "{{name}}",
    "app_version": {{version}},
    "env_name": "{{env}}",
    "db_connect": "{{db}}"
  }
  ```

* **Value Files:**

  `/path/to/values/uat_values.json`

  ```json
  {
    "name": "SimpleApp",
    "version": 1.0,
    "env": "uat",
    "db_host": "localhost",
    "db_port": 1234,
    "db": "jdbc:mysql://{{db_host}}:{{db_port}}/"
  }
  ```

  `/path/to/values/prod_values.json`

  ```json
  {
    "name": "SimpleApp",
    "version": 1.0,
    "env": "prod",
    "db_host": "1.2.3.4",
    "db_port": 9876,
    "db": "jdbc:mysql://{{db_host}}:{{db_port}}/"
  }
  ```

* **Plugin configuration:** The project's `pom.xml` can look something like
  below, with two different maven profiles `uat_build` and `prod_build`. They
  both use the same template, but the value file path and output file paths can
  be switched as per the activated profile.

  ```xml
  <project>
      ...
      <profiles>
          ...
          <profile>
              <!-- Profile for UAT Environment -->
              <id>uat_build</id>
              <activation>
                  <property>
                      <name>uat_build</name>
                  </property>
              </activation>
  
              ...
  
              <build>
                  <plugins>
                      ...
          
                      <plugin>
                          <groupId>com.github.chitralverma</groupId>
                          <artifactId>jinja-maven-plugin</artifactId>
                          <version>${latest.plugin.version}</version>
                          <configuration>
                              <skip>false</skip>
                              <failOnMissingValues>true</failOnMissingValues>
                              <overwriteOutput>false</overwriteOutput>
                              <resourceSet>
                                  <resource>
                                      <templateFilePath>/path/to/template/a_template.j2</templateFilePath>
                                      <valueFiles>
                                          <param>/path/to/values/uat_values.json</param>
                                      </valueFiles>
                                      <outputFilePath>src/main/resources/jinja/results/uat_config.json</outputFilePath>
                                  </resource>
                              </resourceSet>
                          </configuration>
                      </plugin>

                      ...
                  </plugins>
              </build>
          </profile>
  
          <profile>
              <!-- Profile for PROD Environment -->
              <id>prod_build</id>
              <activation>
                  <property>
                      <name>prod_build</name>
                  </property>
              </activation>
  
              ...
  
              <build>
                  <plugins>
                      ...
          
                      <plugin>
                          <groupId>com.github.chitralverma</groupId>
                          <artifactId>jinja-maven-plugin</artifactId>
                          <version>${latest.plugin.version}</version>
                          <configuration>
                              <skip>false</skip>
                              <failOnMissingValues>true</failOnMissingValues>
                              <overwriteOutput>false</overwriteOutput>
                              <resourceSet>
                                  <resource>
                                      <templateFilePath>/path/to/template/a_template.j2</templateFilePath>
                                      <valueFiles>
                                          <param>/path/to/values/prod_values.json</param>
                                      </valueFiles>
                                      <outputFilePath>src/main/resources/jinja/results/prod_config.json</outputFilePath>
                                  </resource>
                              </resourceSet>
                          </configuration>
                      </plugin>

                      ...
                  </plugins>
              </build>
          </profile>
  
          ...
      </profiles>
  
      ...
  </project>
  ```

* **Execution:**
    * Going back to scenario (i) - Rendering everything in one pass - this can
      be achieved by removing the maven profiles altogether and create two
      separate resource in the plugin directly, like below,

      ```xml
      <project>
          ...
          
              <build>
                  ...
        
                  <plugins>
                      ...
          
                      <plugin>
                          <groupId>com.github.chitralverma</groupId>
                          <artifactId>jinja-maven-plugin</artifactId>
                          <version>${latest.plugin.version}</version>
                          <configuration>
                              <skip>false</skip>
                              <failOnMissingValues>true</failOnMissingValues>
                              <overwriteOutput>false</overwriteOutput>
                              <resourceSet>
      
                                  <!-- Profile for UAT Environment -->
                                  <resource>
                                      <templateFilePath>/path/to/template/a_template.j2</templateFilePath>
                                      <valueFiles>
                                          <param>/path/to/values/uat_values.json</param>
                                      </valueFiles>
                                      <outputFilePath>src/main/resources/jinja/results/uat_config.json</outputFilePath>
                                  </resource>
                                    
                                  <!-- Profile for PROD Environment -->
                                  <resource>
                                      <templateFilePath>/path/to/template/a_template.j2</templateFilePath>
                                      <valueFiles>
                                          <param>/path/to/values/prod_values.json</param>
                                      </valueFiles>
                                      <outputFilePath>src/main/resources/jinja/results/prod_config.json</outputFilePath>
                                  </resource>
      
                              </resourceSet>
                          </configuration>
                      </plugin>
        
                      ...
                  </plugins>
                
                  ...
              </build>
      
          ...
      </project>
      ```

    * As for the scenario (ii) - Rendering selectively by triggers - this can be
      achieved by running either of the below mentioned commands as per the
      requirement. See Plugin configuration section above.

      ```commandline
      # For UAT environment
      mvn clean package -Duat_build
      
      # For PROD environment
      mvn clean package -Dprod_build
      ```

* **Output Files:**

  `src/main/resources/jinja/results/uat_config.json`

  ```json
  {
    "app_name": "SimpleApp",
    "app_version": 1.0,
    "env_name": "uat",
    "db_connect": "jdbc:mysql://localhost:1234/"
  }
  ```

  `src/main/resources/jinja/results/prod_config.json`

  ```json
  {
    "app_name": "SimpleApp",
    "app_version": 1.0,
    "env_name": "prod",
    "db_connect": "jdbc:mysql://1.2.3.4:9876/"
  }
  ```