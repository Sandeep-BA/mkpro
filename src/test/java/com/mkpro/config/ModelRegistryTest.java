package com.mkpro.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ModelRegistry & models.yaml Tests")
class ModelRegistryTest {

    @Test
    @DisplayName("Gemini 3.8 flash models should be registered in ModelRegistry")
    void testGemini38ModelsInModelRegistry() {
        assertNotNull(ModelRegistry.GEMINI_MODELS, "GEMINI_MODELS list should not be null");
        assertTrue(ModelRegistry.GEMINI_MODELS.contains("gemini-3.8-flash"),
                "GEMINI_MODELS should contain 'gemini-3.8-flash'");
        assertTrue(ModelRegistry.GEMINI_MODELS.contains("gemini-3.8-flash-lite"),
                "GEMINI_MODELS should contain 'gemini-3.8-flash-lite'");

        List<String> allModels = ModelRegistry.getAllModels();
        assertNotNull(allModels, "getAllModels should not return null");
        assertTrue(allModels.contains("gemini-3.8-flash"),
                "getAllModels should contain 'gemini-3.8-flash'");
        assertTrue(allModels.contains("gemini-3.8-flash-lite"),
                "getAllModels should contain 'gemini-3.8-flash-lite'");
    }

    @Test
    @DisplayName("models.yaml resource should be valid and contain Gemini 3.8 flash models")
    @SuppressWarnings("unchecked")
    void testModelsYamlResourceContainsGemini38Models() throws Exception {
        try (InputStream is = getClass().getResourceAsStream("/models.yaml")) {
            assertNotNull(is, "models.yaml resource should exist in classpath");
            ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
            Map<String, Object> data = mapper.readValue(is, Map.class);
            assertNotNull(data, "Parsed YAML data should not be null");
            assertTrue(data.containsKey("gemini"), "YAML should have 'gemini' key");

            List<String> geminiModels = (List<String>) data.get("gemini");
            assertNotNull(geminiModels, "gemini section should not be null");
            assertTrue(geminiModels.contains("gemini-3.8-flash"),
                    "models.yaml gemini list should contain 'gemini-3.8-flash'");
            assertTrue(geminiModels.contains("gemini-3.8-flash-lite"),
                    "models.yaml gemini list should contain 'gemini-3.8-flash-lite'");
        }
    }
}
