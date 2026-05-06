package com.HackerEarth.Hackathon.ubidbridge.service.translator;

import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
public class MunicipalTranslator implements DepartmentTranslator{

    @Override
    public String getDepartmentId() {
        return "DEPT_B";
    }

    @Override
    public Map<String, Object> translate(Map<String, Object> swsFields, String eventType) {
        Map<String, Object> translated = new HashMap<>();

        switch (eventType) {
            case "ADDRESS_CHANGE" -> {
                // Municipal uses a single concatenated address string
                String fullAddress = buildFullAddress(swsFields);
                translated.put("mun_address",   fullAddress);
                translated.put("mun_ward_no",   swsFields.getOrDefault("wardNumber", ""));
                translated.put("mun_zone",       swsFields.getOrDefault("zone", ""));
                mapIfPresent(swsFields, "pincode", translated, "mun_pincode");
            }
            case "NAME_CHANGE" -> {
                // Municipal uses full_name as a single field
                String fullName = buildFullName(swsFields);
                translated.put("mun_full_name", fullName);
            }
            case "DOB_CHANGE" -> {
                mapIfPresent(swsFields, "dateOfBirth", translated, "mun_birth_date");
            }
            case "CONTACT_CHANGE" -> {
                mapIfPresent(swsFields, "mobile", translated, "mun_contact_number");
            }
            default -> swsFields.forEach((k, v) -> translated.put("mun_" + k, v));
        }

        translated.put("mun_data_source", "SWS_BRIDGE");
        return translated;
    }

    private String buildFullAddress(Map<String, Object> fields) {
        StringBuilder sb = new StringBuilder();
        appendIfPresent(sb, fields, "street");
        appendIfPresent(sb, fields, "city");
        appendIfPresent(sb, fields, "district");
        appendIfPresent(sb, fields, "state");
        appendIfPresent(sb, fields, "pincode");
        return sb.toString().trim();
    }

    private String buildFullName(Map<String, Object> fields) {
        String first  = (String) fields.getOrDefault("firstName", "");
        String middle = (String) fields.getOrDefault("middleName", "");
        String last   = (String) fields.getOrDefault("lastName", "");
        return (first + " " + middle + " " + last).replaceAll("\\s+", " ").trim();
    }

    private void appendIfPresent(StringBuilder sb, Map<String, Object> fields, String key) {
        Object val = fields.get(key);
        if (val != null && !val.toString().isBlank()) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(val);
        }
    }

    private void mapIfPresent(Map<String, Object> source, String sourceKey,
                              Map<String, Object> target, String targetKey) {
        if (source.containsKey(sourceKey) && source.get(sourceKey) != null) {
            target.put(targetKey, source.get(sourceKey));
        }
    }
}
