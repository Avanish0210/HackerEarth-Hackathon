package com.HackerEarth.Hackathon.ubidbridge.service.translator;

import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
public class UtilityTranslator implements DepartmentTranslator {

    @Override
    public String getDepartmentId() {
        return "DEPT_C";
    }

    @Override
    public Map<String, Object> translate(Map<String, Object> swsFields, String eventType) {
        Map<String, Object> translated = new HashMap<>();

        switch (eventType) {
            case "ADDRESS_CHANGE" -> {
                // Utility uses uppercase and prefixed field names
                mapUpperIfPresent(swsFields, "street",   translated, "UTL_STREET");
                mapUpperIfPresent(swsFields, "city",     translated, "UTL_CITY");
                mapUpperIfPresent(swsFields, "state",    translated, "UTL_STATE");
                mapUpperIfPresent(swsFields, "pincode",  translated, "UTL_PINCODE");
                mapUpperIfPresent(swsFields, "district", translated, "UTL_DISTRICT");
            }
            case "NAME_CHANGE" -> {
                mapUpperIfPresent(swsFields, "firstName",  translated, "UTL_FNAME");
                mapUpperIfPresent(swsFields, "lastName",   translated, "UTL_LNAME");
                mapUpperIfPresent(swsFields, "middleName", translated, "UTL_MNAME");
            }
            case "DOB_CHANGE" -> {
                mapIfPresent(swsFields, "dateOfBirth", translated, "UTL_DOB");
            }
            case "CONTACT_CHANGE" -> {
                mapIfPresent(swsFields, "mobile", translated, "UTL_MOBILE");
                mapUpperIfPresent(swsFields, "email", translated, "UTL_EMAIL");
            }
            default -> swsFields.forEach((k, v) ->
                    translated.put("UTL_" + k.toUpperCase(), v));
        }

        translated.put("UTL_SRC", "SWS");
        return translated;
    }

    private void mapUpperIfPresent(Map<String, Object> source, String sourceKey,
                                   Map<String, Object> target, String targetKey) {
        Object val = source.get(sourceKey);
        if (val != null) {
            target.put(targetKey, val.toString().toUpperCase());
        }
    }

    private void mapIfPresent(Map<String, Object> source, String sourceKey,
                              Map<String, Object> target, String targetKey) {
        if (source.containsKey(sourceKey) && source.get(sourceKey) != null) {
            target.put(targetKey, source.get(sourceKey));
        }
    }
}
