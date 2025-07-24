// File: src/main/java/com/arp/core/models/Anaesthetist.java
package com.arp_1.core.models;

import java.util.*;

public class Anaesthetist {
    private String id;
    private String type; // Junior, Senior
    private boolean isActive;
    private Map<String, Integer> locationPreferences; // 0=not qualified, 1=qualified+preferred, 2=qualified+less
                                                      // preferred

    public Anaesthetist(String id, String type, boolean isActive) {
        this.id = id;
        this.type = type;
        this.isActive = isActive;
        this.locationPreferences = new HashMap<>();
    }

    public boolean isQualifiedFor(String workstation) {
        return locationPreferences.getOrDefault(workstation, 0) > 0;
    }

    public boolean hasPreferenceFor(String workstation) {
        return locationPreferences.getOrDefault(workstation, 0) == 1;
    }

    public boolean hasLessPreferenceFor(String workstation) {
        return locationPreferences.getOrDefault(workstation, 0) == 2;
    }

    public boolean isJunior() {
        return "Junior".equals(type);
    }

    public boolean isSenior() {
        return "Senior".equals(type);
    }

    public void setLocationPreference(String workstation, int preference) {
        this.locationPreferences.put(workstation, preference);
    }

    // Getters
    public String getId() {
        return id;
    }

    public String getType() {
        return type;
    }

    public boolean isActive() {
        return isActive;
    }

    public Map<String, Integer> getLocationPreferences() {
        return locationPreferences;
    }

    @Override
    public String toString() {
        return String.format("Anaesthetist{id='%s', type='%s', active=%s}", id, type, isActive);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        Anaesthetist that = (Anaesthetist) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}