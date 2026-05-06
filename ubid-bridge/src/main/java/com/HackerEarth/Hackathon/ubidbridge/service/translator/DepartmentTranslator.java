package com.HackerEarth.Hackathon.ubidbridge.service.translator;

import java.util.Map;

public interface DepartmentTranslator {
    /**
     * The department ID this translator handles (matches department_registry.department_id).
     */
    String getDepartmentId();

    /**
     * Translate SWS canonical payload into this department's field schema.
     *
     * @param swsFields  canonical fields from SWS event
     * @param eventType  e.g. ADDRESS_CHANGE, NAME_CHANGE
     * @return translated field map ready to POST to the department system
     */
    Map<String, Object> translate(Map<String, Object> swsFields, String eventType);
}
