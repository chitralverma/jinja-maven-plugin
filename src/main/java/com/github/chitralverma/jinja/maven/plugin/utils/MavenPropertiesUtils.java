/*
 *    Copyright 2021 Chitral Verma
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

package com.github.chitralverma.jinja.maven.plugin.utils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.ValueNode;
import com.google.common.collect.Maps;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.project.MavenProject;

public class MavenPropertiesUtils {

  private MavenPropertiesUtils() {
    throw new IllegalStateException("Utility class");
  }

  public static final ObjectMapper mapper = new ObjectMapper();

  public static void setMavenProperties(
      MavenProject project, Map<String, Object> mavenProperties, Log log)
      throws JsonProcessingException {
    MavenPropertiesUtils.addMavenField(
        "maven_properties", project, mavenProperties, log);

    MavenPropertiesUtils.addMavenField(
        "maven_properties", project.getModel(), mavenProperties, log);

    Map<String, Object> allMavenProperties =
        (Map) mavenProperties.get("maven_properties");
    allMavenProperties.put("properties", project.getProperties());

    log.debug(MavenPropertiesUtils.mapper.writeValueAsString(mavenProperties));
  }

  private static void addMavenField(
      String currentPath, Object o, Map<String, Object> result, Log log) {
    if (o != null) {
      Field[] declaredFields = o.getClass().getDeclaredFields();
      ObjectNode objectNode = mapper.createObjectNode();

      for (Field declaredField : declaredFields) {
        declaredField.setAccessible(true);

        String declaredFieldName = declaredField.getName();

        try {
          Object value = declaredField.get(o);

          HashMap<Object, Object> map = Maps.newHashMap();
          map.put(declaredFieldName, value);

          mapper.convertValue(map, JsonNode.class);
          objectNode.putPOJO(declaredFieldName, value);
        } catch (Exception e) {
          log.debug(
              String.format(
                  "Unable to process field with name '%s'.",
                  declaredFieldName));
        } finally {
          flattenObjToMap(currentPath, objectNode, result);
        }
      }
    }
  }

  private static void flattenObjToMap(
      String currentPath, JsonNode node, Map<String, Object> result) {
    if (node.isObject()) {
      String pathPrefix =
          currentPath.isEmpty() ? StringUtils.EMPTY : currentPath + ".";

      ObjectNode objectNode = (ObjectNode) node;
      Iterator<Map.Entry<String, JsonNode>> jsonNodeIterator =
          objectNode.fields();

      if (!currentPath.isEmpty()) {
        Map<String, Object> obj =
            mapper.convertValue(
                objectNode, new TypeReference<Map<String, Object>>() {});
        result.put(currentPath, obj);
      }

      jsonNodeIterator.forEachRemaining(
          (Map.Entry<String, JsonNode> e) ->
              flattenObjToMap(pathPrefix + e.getKey(), e.getValue(), result));
    } else if (node.isArray()) {
      ArrayNode arrayNode = (ArrayNode) node;

      if (!currentPath.isEmpty()) {
        ArrayList<Object> obj =
            mapper.convertValue(
                arrayNode, new TypeReference<ArrayList<Object>>() {});
        result.put(currentPath, obj);
      }

      for (int i = 0; i < arrayNode.size(); i++) {
        flattenObjToMap(
            String.format("%s[%s]", currentPath, i), arrayNode.get(i), result);
      }
    } else {
      ValueNode valueNode = (ValueNode) node;
      result.put(currentPath, valueNode.asText());
    }
  }
}
