package com.HackerEarth.Hackathon.ubidbridge.service;


import com.HackerEarth.Hackathon.ubidbridge.service.translator.DepartmentTranslator;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Auto-discovers all DepartmentTranslator beans on startup.
 * New departments are onboarded by adding one new @Component translator —
 * no changes to this class needed.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TranslatorRegistry {

    // Spring injects all DepartmentTranslator implementations automatically
    private final List<DepartmentTranslator> translators;

    private final Map<String, DepartmentTranslator> registry = new HashMap<>();

    @PostConstruct
    public void init() {
        for (DepartmentTranslator t : translators) {
            registry.put(t.getDepartmentId(), t);
            log.info("Registered translator for department: {}", t.getDepartmentId());
        }
    }

    /**
     * Translate SWS fields for a specific department.
     *
     * @return translated payload, or empty map if no translator found
     */
    public Map<String, Object> translate(String departmentId,
                                         Map<String, Object> swsFields,
                                         String eventType) {
        return Optional.ofNullable(registry.get(departmentId))
                .map(t -> t.translate(swsFields, eventType))
                .orElseGet(() -> {
                    log.warn("No translator found for department: {}. " +
                            "Passing fields through unchanged.", departmentId);
                    return new HashMap<>(swsFields);
                });
    }

    public boolean hasTranslator(String departmentId) {
        return registry.containsKey(departmentId);
    }

    public List<String> registeredDepartments() {
        return List.copyOf(registry.keySet());
    }
}
