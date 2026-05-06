package com.HackerEarth.Hackathon.ubidbridge.service.translator;

import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
public class RevenueTranslator implements DepartmentTranslator {

    @Override
    public String getDepartmentId() {
        return "DEPT_A";
    }

    @Override
    public Map<String, Object> translate(Map<String, Object> swsFields, String eventType) {
        Map<String, Object> translated = new HashMap<>();

        switch (eventType) {
            case "ADDRESS_CHANGE" -> {
                // SWS canonical → Revenue dept schema
                mapIfPresent(swsFields, "street",       translated, "rev_street_address");
                mapIfPresent(swsFields, "city",         translated, "rev_city");
                mapIfPresent(swsFields, "state",        translated, "rev_state_code");
                mapIfPresent(swsFields, "pincode",      translated, "rev_pin");
                mapIfPresent(swsFields, "district",     translated, "rev_district");
            }
            case "NAME_CHANGE" -> {
                mapIfPresent(swsFields, "firstName",    translated, "rev_first_name");
                mapIfPresent(swsFields, "lastName",     translated, "rev_last_name");
                mapIfPresent(swsFields, "middleName",   translated, "rev_middle_name");
            }
            case "DOB_CHANGE" -> {
                mapIfPresent(swsFields, "dateOfBirth",  translated, "rev_dob");
            }
            case "CONTACT_CHANGE" -> {
                mapIfPresent(swsFields, "mobile",       translated, "rev_mobile_no");
                mapIfPresent(swsFields, "email",        translated, "rev_email_id");
            }
            default -> {
                // Pass through all fields with rev_ prefix for unknown event types
                swsFields.forEach((k, v) -> translated.put("rev_" + k, v));
            }
        }

        // Always include metadata
        translated.put("rev_last_updated_source", "SWS");
        return translated;
    }

    private void mapIfPresent(Map<String, Object> source, String sourceKey,
                              Map<String, Object> target, String targetKey) {
        if (source.containsKey(sourceKey) && source.get(sourceKey) != null) {
            target.put(targetKey, source.get(sourceKey));
        }
    }
}
